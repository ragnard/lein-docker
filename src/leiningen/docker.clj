(ns leiningen.docker
  (:require [leiningen.core.main :as main]
            [leiningen.core.eval :as eval]
            [leiningen.core.project :as project]
            [robert.hooke :refer [add-hook]]
            [clojure.java.io :as io])
  (:import [java.net ServerSocket]))

;; docker run -v /home/vagrant/torus-pong/deps:/root/.m2/repository -v /home/vagrant/torus-pong:/opt/torus-pong -e LEIN_ROOT=true -w /opt/torus-pong -i -t 7a3d220e0f91 lein

(defn- project-root
  [project]
  (or (-> project :docker :root)
      (str "/opt/" (:name project))))

(defn- mounts
  [project]
  (let [root (:root project)
        project-mount [(:root project) (project-root project)]]
    (mapcat (fn [[host container]]
              ["-v" (str host ":" container)])
            (concat [project-mount]
                    (-> project :docker :mounts)))))

(defn- available-port
  []
  (inc (.getLocalPort (ServerSocket. 0))))

(defn- ports
  [project]
  (let [ports (-> project :docker :ports)]
    (concat (mapcat (fn [port]
                      (if-let [[host-port container-port] port]
                        ["-p" (str host-port ":" container-port)]
                        ["-p" port]))
                    ports))))

(defn- env
  [project]
  (let [env (-> project :docker :env)]
    (concat (mapcat (fn [[k v]] ["-e" (str k "=" v)])
                    env))))

(defn- docker-deps-cache
  [project]
  (let [deps-cache-dir-name ".lein-docker-deps"
        deps-cache-dir (str (:root project) "/" deps-cache-dir-name)]
    (.mkdirs (io/file deps-cache-dir))
    ["-v" (str  deps-cache-dir ":" "/root/.m2/repository")]))

(defn extra-docker-args
  [project]
  (-> project :docker :extra-args))

(defn- docker-cmd
  [project args]
  (let [root  (:root project)
        docker (:docker project)
        image (or (:image docker)
                  (main/abort "No docker image specified"))
        repl-port (available-port)]
    (concat ["docker" "run"]
            ["-w" (project-root project)]
            (docker-deps-cache project)
            (mounts project)
            (ports project)
            ["-p" (str repl-port ":" repl-port)]
            (env project)
            ["-i" "-t"]
            (extra-docker-args project)
            [image]
            ["lein"]
            ["update-in" ":repl-options" "assoc" ":port" (str repl-port)]
            ["--"]
            args)))

(defn docker
  "Run a lein task in a docker container"
  [project & args]
  (let [cmd (docker-cmd project args)]
    (println "Running leiningen in docker container using command:")
    (println (pr-str cmd) "\n")
    (apply eval/sh cmd)))
