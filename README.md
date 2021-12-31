# gcp-api

gcp-api is a Clojure library which provides programmatic access to GCP services from your Clojure program.

## Rationale

See aws-api's rationale [here](https://github.com/cognitect-labs/aws-api#rationale).

## Approach

Much the same as aws-api's [approach](https://github.com/cognitect-labs/aws-api#approach), this library publishes
descriptor files that specify the operations, inputs, and outputs. These descriptor files are created from
the [GCP discovery documents](https://developers.google.com/discovery/v1/reference).

The generated descriptor files are published in a separate repository
located [here](https://github.com/ComputeSoftware/gcp-api-descriptors).

## Usage

Using gcp-api requires you to add `gcp-api/gcp-api` and the service(s) of your choosing. In the below example we add
the [GCP Compute Engine API](https://cloud.google.com/compute/docs/reference/rest/v1/) `gcp-api/compute`. Note that you
must replace part of the `:deps/root` path with the API version you want to use.

To use, for example, the [compute v1 api](https://cloud.google.com/compute/docs/reference/rest/v1), add the following to
`deps.edn`

```clojure
gcp-api/gcp-api {:git/url "https://github.com/ComputeSoftware/gcp-api.git"
                 ;; update this sha to the most recent
                 :sha     "d52e1646077a49fd0f5fadede7cc6e971a79127a"}
gcp-api/compute {:git/url   "https://github.com/ComputeSoftware/gcp-api-descriptors.git"
                 ;; update this sha to the most recent
                 :sha       "7b00d7c1ff31b03e9cd7df4189ae8dd8e533eec4"
                 ;; you can find other roots by browsing at directories in
                 ;; https://github.com/ComputeSoftware/gcp-api-descriptors
                 :deps/root "compute/v1"}
```

```clojure
(require '[compute.gcp.api :as gcp-api])
;; Create a client:
(def compute-v1
  (gcp-api/client {:api     :compute
                   :version "v1"}))
;; Ask what ops your client can perform
;; It will return a descriptive map describing the supported parameters, response shape, and other info
(gcp-api/ops compute-v1)
=> {...
    "compute.instances.list" {:compute.gcp.descriptor/http-method :get
                              :compute.gcp.descriptor/path        "projects/{project}/zones/{zone}/instances"
                              :compute.gcp.descriptor/response    ...
                              :compute.gcp.descriptor/parameters  {"zone" {"type" "string"
                                                                           "description" "The name of the zone for this request."
                                                                           "required" true
                                                                           "pattern" "[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?"
                                                                           "location" "path"}
                                                                   "maxResults" {"type" "integer"
                                                                                 "description" "The maximum number of results per page that should be returned. If the number of available results is larger than `maxResults`, Compute Engine returns a `nextPageToken` that can be used to get the next page of results in subsequent list requests. Acceptable values are `0` to `500`, inclusive. (Default: `500`)"
                                                                                 "default" "500"
                                                                                 "format" "uint32"
                                                                                 "minimum" "0"
                                                                                 "location" "query"}
                                                                   ...}
                              :compute.gcp.descriptor/description "Retrieves the list of instances contained within the specified zone."}
    ...}

;; Look up docs for an operation:
(gcp-api/doc compute-v1 "compute.instances.list")


;; Do stuff:
(gcp-api/invoke
  compute-v1
  {:op      "compute.instances.list"
   :request {:project "my-project"
             :zone    "us-central1-c"}})
=> {:kind     "compute#instanceList"
    :id       "projects/my-project/zones/us-central1-c/instances"
    :selfLink "https://www.googleapis.com/compute/v1/projects/my-project/zones/us-central1-c/instances"
    :items    [{:description ""
                :name        "example-01"
                ...}]}

```

## Prior Art

* Cognitect's [aws-api](https://github.com/cognitect-labs/aws-api)

## License

Copyright © 2020 Compute Software

Distributed under the Eclipse Public License either version 2.0 or (at your option) any later version.
