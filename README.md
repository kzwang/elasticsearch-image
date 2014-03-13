Image Plugin for Elasticsearch
==================================

[![Build Status](https://travis-ci.org/kzwang/elasticsearch-image.png?branch=master)](https://travis-ci.org/kzwang/elasticsearch-image)

The Image Plugin is an Content Based Image Retrieval Plugin for Elasticsearch using [LIRE (Lucene Image Retrieval)](https://code.google.com/p/lire/). It allows users to index images and search for similar images.

It adds an `image` field type and an `image` query

See [http://demo.elasticsearch-image.com](http://demo.elasticsearch-image.com) for a demo of the plugin

In order to install the plugin, simply run: `bin/plugin -install com.github.kzwang/elasticsearch-image/1.1.0`.

|     Image Plugin          |  elasticsearch    | Release date |
|---------------------------|-------------------|:------------:|
| 1.2.0-SNAPSHOT (master)   | 1.0.1             |              |
| 1.1.0                     | 1.0.1             | 2014-03-13   |
| 1.0.0                     | 1.0.1             | 2014-03-05   |


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
                "boost": 2.1,
                "limit": 100
            }
        }
    }
}'
```
`feature` should be one of the features in the mapping.  **Mandatory**

`image` base64 of image to search.  **Mandatory**

`hash` should be same to the hash set in mapping.  **Optional**

`limit` limit the number of results returned (per shard) for scoring. **Optional, only works when `hash` is specified**

`boost` score boost  **Optional**


### Supported Image Formats
Images are processed by Java ImageIO, supported formats can be found [here](http://docs.oracle.com/javase/7/docs/api/javax/imageio/package-summary.html)

Additional formats can be supported by ImageIO plugins, for example [TwelveMonkeys](https://github.com/haraldk/TwelveMonkeys)


### Supported Features
[`AUTO_COLOR_CORRELOGRAM`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/AutoColorCorrelogram.java),  [`BINARY_PATTERNS_PYRAMID`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/BinaryPatternsPyramid.java), [`CEDD`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/CEDD.java), [`SIMPLE_COLOR_HISTOGRAM`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/SimpleColorHistogram.java), [`COLOR_LAYOUT`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/ColorLayout.java), [`EDGE_HISTOGRAM`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/EdgeHistogram.java), [`FCTH`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/FCTH.java), [`GABOR`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/Gabor.java), [`JCD`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/JCD.java), [`JOINT_HISTOGRAM`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/joint/JointHistogram.java), [`JPEG_COEFFICIENT_HISTOGRAM`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/JpegCoefficientHistogram.java), [`LOCAL_BINARY_PATTERNS`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/LocalBinaryPatterns.java), [`LUMINANCE_LAYOUT`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/LuminanceLayout.java), [`OPPONENT_HISTOGRAM`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/OpponentHistogram.java), [`PHOG`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/PHOG.java), [`ROTATION_INVARIANT_LOCAL_BINARY_PATTERNS`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/RotationInvariantLocalBinaryPatterns.java), [`SCALABLE_COLOR`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/ScalableColor.java), [`TAMURA`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/imageanalysis/Tamura.java)


### Supported Hash Mode
[`BIT_SAMPLING`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/indexing/hashing/BitSampling.java), [`LSH`](https://code.google.com/p/lire/source/browse/trunk/src/main/java/net/semanticmetadata/lire/indexing/hashing/LocalitySensitiveHashing.java)

Hash will increase search speed with large data sets

See [Large image data sets with LIRE – some new numbers](http://www.semanticmetadata.net/2013/03/20/large-image-data-sets-with-lire-some-new-numbers/) 


##ChangeLog

#### 1.1.0 (2014-03-13)

- Added `limit` in `image` query
- Added plugin version in es-plugin.properties

#### 1.0.0 (2014-03-05)

- initial release