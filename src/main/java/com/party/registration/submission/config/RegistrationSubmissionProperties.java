package com.party.registration.submission.config;

import com.party.registration.document.entity.DocumentType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.EnumSet;
import java.util.Set;

@Component
@Validated
@ConfigurationProperties(prefix = "app.registration.submission")
@Getter
@Setter
public class RegistrationSubmissionProperties {
    @NotEmpty
    private Set<DocumentType> requiredDocumentTypes = EnumSet.of(DocumentType.RECOMMENDATION_LETTER);

    @AssertTrue(message = "Required documents must include RECOMMENDATION_LETTER")
    public boolean isRecommendationLetterRequired() {
        return requiredDocumentTypes == null
                || requiredDocumentTypes.contains(DocumentType.RECOMMENDATION_LETTER);
    }
}
