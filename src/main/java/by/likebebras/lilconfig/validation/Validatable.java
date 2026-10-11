package by.likebebras.lilconfig.validation;

/** Validation hook for root or nested models. Throw on invalid values. */
public interface Validatable {
    void validate();
}
