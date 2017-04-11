Provision a Spark Cluster on ECS
======

# Usage

Change to the Provision directory and execute Chrome or Chromium with disabled web security
to allow [CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS):

    cd CIMApplication/Provision
    chromium-browser --disable-web-security --user-data-dir ./index.html

Follow the wizard steps.

# Notes

The AWS API is encapsulated in this repository, rather than [loading it via Amazon](https://sdk.amazonaws.com/js/aws-sdk-2.7.20.min.js) because the IAM API is not included.
That is, rather than loading the AWS Javascript SDK from Amazon,
the [Building SDK for Browsers](http://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/building-sdk-for-browsers.html) page
was used to create a minified library including only the relevant API subset (ECS, EC2 and IAM).
This is found in the /js subdirectory as "aws.X.YY.min.js".
