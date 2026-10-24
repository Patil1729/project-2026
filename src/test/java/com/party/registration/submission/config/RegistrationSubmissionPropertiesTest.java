package com.party.registration.submission.config;

import com.party.registration.document.entity.DocumentType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationSubmissionPropertiesTest {
    @Test
    void defaultsRequireTheRecommendationLetter() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var properties = new RegistrationSubmissionProperties();
            assertTrue(factory.getValidator().validate(properties).isEmpty());
            assertEquals(EnumSet.of(DocumentType.RECOMMENDATION_LETTER), properties.getRequiredDocumentTypes());
        }
    }

    @Test
    void emptyConfigurationIsInvalid() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var properties = new RegistrationSubmissionProperties();
            properties.setRequiredDocumentTypes(EnumSet.noneOf(DocumentType.class));
            assertFalse(factory.getValidator().validate(properties).isEmpty());
        }
    }

    @Test
    void recommendationCannotBeRemovedFromTheRequiredList() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var properties = new RegistrationSubmissionProperties();
            properties.setRequiredDocumentTypes(EnumSet.of(DocumentType.IDENTITY_PROOF));
            assertFalse(factory.getValidator().validate(properties).isEmpty());
        }
    }

    @Test
    void additionalDocumentTypesAreSupported() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var properties = new RegistrationSubmissionProperties();
            properties.setRequiredDocumentTypes(EnumSet.of(DocumentType.IDENTITY_PROOF, DocumentType.RECOMMENDATION_LETTER));
            assertTrue(factory.getValidator().validate(properties).isEmpty());
        }
    }
}
