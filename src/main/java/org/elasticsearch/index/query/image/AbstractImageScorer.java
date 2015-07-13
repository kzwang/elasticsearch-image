package org.elasticsearch.index.query.image;

import net.semanticmetadata.lire.imageanalysis.LireFeature;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BytesRef;
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
    private BinaryDocValues binaryDocValues;

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

        if (binaryDocValues == null) {
            AtomicReader atomicReader = (AtomicReader) reader;
            binaryDocValues = atomicReader.getBinaryDocValues(luceneFieldName);
        }

        try {
            BytesRef bytesRef = binaryDocValues.get(docID());
            LireFeature docFeature = lireFeature.getClass().newInstance();
            docFeature.setByteArrayRepresentation(bytesRef.bytes);

            float distance = lireFeature.getDistance(docFeature);
            float score;
            if (Float.compare(distance, 1.0f) <= 0) { // distance less than 1, consider as same image
                score = 2f - distance;
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
