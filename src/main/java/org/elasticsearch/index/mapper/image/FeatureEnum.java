package org.elasticsearch.index.mapper.image;


import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.imageanalysis.joint.JointHistogram;

/**
 * Features supported by LIRE
 * Subclass of {@link LireFeature}
 */
public enum FeatureEnum {

    AUTO_COLOR_CORRELOGRAM(AutoColorCorrelogram.class),
    BINARY_PATTERNS_PYRAMID(BinaryPatternsPyramid.class),
    CEDD(CEDD.class),
    SIMPLE_COLOR_HISTOGRAM(SimpleColorHistogram.class),
    COLOR_LAYOUT(ColorLayout.class),
    EDGE_HISTOGRAM(EdgeHistogram.class),
    FCTH(FCTH.class),
    GABOR(Gabor.class),
    JCD(JCD.class),
    JOINT_HISTOGRAM(JointHistogram.class),
    JPEG_COEFFICIENT_HISTOGRAM(JpegCoefficientHistogram.class),
    LOCAL_BINARY_PATTERNS(LocalBinaryPatterns.class),
    LUMINANCE_LAYOUT(LuminanceLayout.class),
    OPPONENT_HISTOGRAM(OpponentHistogram.class),
    PHOG(PHOG.class),
    ROTATION_INVARIANT_LOCAL_BINARY_PATTERNS(RotationInvariantLocalBinaryPatterns.class),
    SCALABLE_COLOR(ScalableColor.class),
    TAMURA(Tamura.class),
    ;

    private Class<? extends LireFeature> featureClass;

    FeatureEnum(Class<? extends LireFeature> featureClass) {
        this.featureClass = featureClass;
    }

    public Class<? extends LireFeature> getFeatureClass() {
        return featureClass;
    }

    public static FeatureEnum getByName(String name) {
        return valueOf(name.toUpperCase());
    }

}
