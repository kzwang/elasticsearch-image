Image Plugin for Elasticsearch
==================================

[![Build Status](https://travis-ci.org/kzwang/elasticsearch-image.png?branch=master)](https://travis-ci.org/kzwang/elasticsearch-image)

The Image Plugin is an Content Based Image Retrieval Plugin for Elasticsearch using [LIRE (Lucene Image Retrieval)](https://code.google.com/p/lire/).
It adds an `image` field type and an `image` query


|     Image Plugin          |  elasticsearch    |   LIRE         | Release date |
|---------------------------|-------------------|----------------|:------------:|
| 1.0.0-SNAPSHOT (master)   | 1.0.1             | 0.9.4-SNAPSHOT |              |


## Example
#### Create Mapping
```sh
curl -XPUT 'localhost:9200/test/test/_mapping' -d '{
    "test": {
        "properties": {
            "my_img": {
                "type": "image",
                "feature": {
                    "CEDD": {
                        "hash": "BIT_SAMPLING"
                    },
                    "JCD": {
                        "hash": ["BIT_SAMPLING", "LSH"]
                    },
                    "FCTH": {}
                }
            }
        }
    }
}'
```
`type` should be `image`. **Mandatory**
`feature` is object of features for index. **Mandatory, at least one is required**
`hash` can be set if you want to search on hash. **Optional**


#### Index Image
```sh
curl -XPOST 'localhost:9200/test/test' -d '{
    "my_img": "... base64 encoded image ..."
}'
```

#### Search Image
```sh
curl -XPOST 'localhost:9200/test/test/_search' -d '{
    "query": {
        "image": {
            "my_img": {
                "feature": "CEDD",
                "image": "... base64 encoded image to search ...",
                "hash": "BIT_SAMPLING",
                "boost": 2.1
            }
        }
    }
}'
```
`feature` should be one of the features in the mapping.  **Mandatory**
`image` base64 of image to search.  **Mandatory**
`hash` should be same to the hash set in mapping.  **Optional**
`boost` score boost  **Optional**


### Supported Image Formats
Images are processed by Java ImageIO, supported formats can be found [here](http://docs.oracle.com/javase/7/docs/api/javax/imageio/package-summary.html)
Additional formats can be supported by ImageIO plugins, for example [TwelveMonkeys](https://github.com/haraldk/TwelveMonkeys)

### Supported Features
`AUTO_COLOR_CORRELOGRAM`, `BINARY_PATTERNS_PYRAMID`, `CEDD`, `SIMPLE_COLOR_HISTOGRAM`, `COLOR_LAYOUT`, `EDGE_HISTOGRAM`, `FCTH`, `GABOR`, `JCD`, `JOINT_HISTOGRAM`, `JPEG_COEFFICIENT_HISTOGRAM`, `LOCAL_BINARY_PATTERNS`, `LUMINANCE_LAYOUT`, `OPPONENT_HISTOGRAM`, `PHOG`, `ROTATION_INVARIANT_LOCAL_BINARY_PATTERNS`, `SCALABLE_COLOR`, `TAMURA`


### Supported Hash Mode
`BIT_SAMPLING`, `LSH`
Hash will increase search speed with large data sets
See [Large image data sets with LIRE – some new numbers](http://www.semanticmetadata.net/2013/03/20/large-image-data-sets-with-lire-some-new-numbers/) 