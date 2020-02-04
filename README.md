# gcp-api

gcp-api is a Clojure library which provides programmatic access to GCP services from your Clojure program.

## Rationale

See aws-api's rationale [here](https://github.com/cognitect-labs/aws-api#rationale). 


## Approach 

Much the same as aws-api's [approach](https://github.com/cognitect-labs/aws-api#rationale), this library publishes descriptor files that specify the operations, inputs, and outputs. These descriptor files are created from the Swagger 2.0 files published by [apis.guru](https://apis.guru/openapi-directory/).

The generated descriptor files are published in a separate repository located [here](https://github.com/ComputeSoftware/gcp-api-descriptors). 

## Usage

Using gcp-api requires you to add `compute/gcp-api` and the service(s) of your choosing. In the below example we add the [GCP Compute Engine API](https://cloud.google.com/compute/docs/reference/rest/v1/) `compute.gcp-api/compute`.

```clojure
{compute/gcp-api         {:git/url "https://github.com/ComputeSoftware/gcp-api.git"
                          :sha     "<most recent sha>"}
 compute.gcp-api/compute {:git/url   "git@github.com:ComputeSoftware/gcp-api-descriptors.git"
                          :sha       "<most recent sha>"
                          :deps/root "compute"}}
```


```clojure
@(invoke
   c
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
