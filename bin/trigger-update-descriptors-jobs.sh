#!/bin/bash

curl -u ${CIRCLE_API_USER_TOKEN}: \
     -d build_parameters[CIRCLE_JOB]=update-descriptor-files \
     https://circleci.com/api/v1.1/project/github/ComputeSoftware/gcp-api/tree/master