package com.sap.cloud.lm.sl.cf.core.validators.parameters;

import com.sap.cloud.lm.sl.common.SLException;

public interface ParameterValidator {

    default boolean isValid(Object container, Object parameter) {
        if (parameter != null) {
            return isValid(parameter);
        }
        return true;
    }

    default boolean isValid(Object parameter) {
        return true;
    }

    default boolean canCorrect() {
        return false;
    }

    default Object attemptToCorrect(Object container, Object parameter) throws SLException, UnsupportedOperationException {
        return attemptToCorrect(parameter);
    }

    default Object attemptToCorrect(Object parameter) throws SLException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    Class<?> getContainerType();

    String getParameterName();

}
