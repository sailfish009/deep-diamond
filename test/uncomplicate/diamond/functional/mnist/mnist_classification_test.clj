(ns uncomplicate.diamond.functional.mnist.mnist-classification-test
  (:require [midje.sweet :refer [facts throws => roughly]]
            [uncomplicate.commons
             [core :refer [let-release Releaseable release with-release]]
             [utils :refer [random-access channel]]]
            [uncomplicate.neanderthal
             [core :refer [subvector view-ge transfer transfer! dim entry ge mrows
                           transfer! view amax imax col]]
             [real :refer [entry!]]
             [native :as neand :refer [native-byte native-float fge]]]
            [uncomplicate.diamond
             [tensor :refer [*diamond-factory* tensor transformer connector transformer
                             desc revert shape input output view-tz batcher]]
             [native :refer [map-file]]
             [dnn :refer [fully-connected network init! train cost infer]]
             [metrics :refer [confusion-matrix contingency-totals classification-metrics]]]
            [uncomplicate.diamond.internal.dnnl.factory :refer [dnnl-factory]]))

(defonce train-images-file (random-access "data/mnist/train-images.idx3-ubyte"))
(defonce train-labels-file (random-access "data/mnist/train-labels.idx1-ubyte"))
(defonce test-images-file (random-access "data/mnist/t10k-images.idx3-ubyte"))
(defonce test-labels-file (random-access "data/mnist/t10k-labels.idx1-ubyte"))

(defonce train-images (map-file train-images-file [60000 1 28 28] :uint8 :nchw :read 16))
(defonce train-labels (map-file train-labels-file [60000] :uint8 :x :read 8))
(defonce train-labels-float (transfer! train-labels (tensor [60000] :float :x)))
(defonce test-images (map-file test-images-file [10000 1 28 28] :uint8 :nchw :read 16))
(defonce test-labels (map-file test-labels-file [10000] :uint8 :x :read 8))
(defonce test-labels-float (transfer! test-labels (tensor [10000] :float :x)))

(defn enc-categories [val-tz]
  (let [val-vector (view val-tz)]
    (let-release [cat-tz (tensor val-tz [(first (shape val-tz)) (inc (long (amax val-vector)))] :float :nc )
                  cat-matrix (view-ge (view cat-tz) (second (shape cat-tz)) (first (shape cat-tz)))]
      (dotimes [j (dim val-vector)]
        (entry! cat-matrix (entry val-vector j) j 1.0))
      cat-tz)))

(defn dec-categories [cat-tz]
  (let [cat-matrix (view-ge (view cat-tz) (second (shape cat-tz)) (first (shape cat-tz)))]
    (let-release [val-tz (tensor cat-tz [(first (shape cat-tz))] :float :x)
                  val-vector (view val-tz)]
      (dotimes [j (dim val-vector)]
        (entry! val-vector j (imax (col cat-matrix j))))
      val-tz)))

(defn test-mnist-classification [fact]
  (with-release [x-mb-tz (tensor fact [512 1 28 28] :float :nchw)
                 y-mb-tz (tensor fact [512 10] :float :nc)
                 net-bp (network fact x-mb-tz
                                 [(fully-connected [256] :relu)
                                  (fully-connected [256] :relu)
                                  (fully-connected [10] :sigmoid)])
                 net (init! (net-bp x-mb-tz :adam))
                 net-infer (net-bp x-mb-tz)
                 crossentropy-cost (cost net y-mb-tz :sigmoid-crossentropy)
                 x-train-bat (batcher train-images (input net))
                 y-train (enc-categories train-labels-float)
                 y-train-bat (batcher y-train y-mb-tz)
                 x-test-bat (batcher test-images (input net-infer))
                 y-test (enc-categories test-labels-float)
                 y-test-bat (batcher (output net-infer) y-test)]
    (facts "MNIST classification tests."
           (time (train net x-train-bat y-train-bat crossentropy-cost 2 []))
           (transfer! net net-infer)
           (take 8 (dec-categories (infer net-infer x-test-bat y-test-bat)))
           => (list 7.0 2.0 1.0 0.0 4.0 1.0 4.0 9.0))))

(with-release [fact (dnnl-factory)]
  (test-mnist-classification fact))

;; "Elapsed time: 791.513226 msecs"
