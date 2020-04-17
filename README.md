# gcp-api

gcp-api is a Clojure library which provides programmatic access to GCP services from your Clojure program.

## Rationale

See aws-api's rationale [here](https://github.com/cognitect-labs/aws-api#rationale). 


## Approach 

Much the same as aws-api's [approach](https://github.com/cognitect-labs/aws-api#rationale), this library publishes descriptor files that specify the operations, inputs, and outputs. 
These descriptor files are created from the [GCP discovery documents](https://developers.google.com/discovery/v1/reference).

The generated descriptor files are published in a separate repository located [here](https://github.com/ComputeSoftware/gcp-api-descriptors). 

## Usage

Using gcp-api requires you to add `gcp-api` and the service(s) of your choosing. 
In the below example we add the [GCP Compute Engine API](https://cloud.google.com/compute/docs/reference/rest/v1/) `gcp-api/compute`.
Note that you must replace part of the `:deps/root` path with the API version you want to use.

```clojure
gcp-api {:git/url "https://github.com/ComputeSoftware/gcp-api.git"
         :sha     "<most recent sha>"}
gcp-api/compute {:git/url   "git@github.com:ComputeSoftware/gcp-api-descriptors.git"
                 :sha       "<most recent sha>"
                 :deps/root "compute/<version>"}
```


```clojure
(require '[compute.gcp.api :as gcp-api])

(def client
  (gcp-api/client {:api     :compute
                   :version "v1"}))

(gcp-api/invoke
  client
  {:op      "compute.instances.list"
   :request {:project "my-project"
             :zone    "us-central1-c"}})
```

## Prior Art

* Cognitect's [aws-api](https://github.com/cognitect-labs/aws-api)

## License

Copyright © 2020 Compute Software

Distributed under the Eclipse Public License either version 2.0 or (at
your option) any later version.
