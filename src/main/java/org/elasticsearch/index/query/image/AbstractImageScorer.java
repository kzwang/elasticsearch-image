package org.elasticsearch.index.query.image;

import net.semanticmetadata.lire.imageanalysis.LireFeature;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.ElasticsearchImageProcessException;

import java.io.IOException;

/**
 * Calculate score for each image
 * score = (1 / distance) * boost
 */
public abstract class AbstractImageScorer extends Scorer {
    private final String luceneFieldName;
    private final LireFeature lireFeature;
    private final IndexReader reader;
    private final float boost;

    protected AbstractImageScorer(Weight weight, String luceneFieldName, LireFeature lireFeature, IndexReader reader,
                                  float boost) {
        super(weight);
        this.luceneFieldName = luceneFieldName;
        this.lireFeature = lireFeature;
        this.reader = reader;
        this.boost = boost;
    }

    @Override
    public float score() throws IOException {
        assert docID() != NO_MORE_DOCS;
        Document document = reader.document(docID());
        try {
            byte[] docImage = null;
            for (IndexableField f : document.getFields()) {
                if (f.name().equals(luceneFieldName)) {
                    docImage = f.binaryValue().bytes;
                    break;
                }
            }
            if (docImage == null) {
                throw new ElasticsearchImageProcessException("Can't find " + luceneFieldName);
            }
            LireFeature docFeature = lireFeature.getClass().newInstance();
            docFeature.setByteArrayRepresentation(docImage);

            float distance = lireFeature.getDistance(docFeature);
            float score;
            if (Float.compare(distance, 1.0f) <= 0) { // distance less than 1, consider as same image
                score = 1f;
            } else {
                score = 1 / distance;
            }
            return score * boost;
        } catch (Exception e) {
            throw new ElasticsearchImageProcessException("Failed to calculate score", e);
        }
    }

    @Override
    public int freq() {
        return 1;
    }
}
