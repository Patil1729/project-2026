package com.party.organization;

import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.repository.OrganizationUnitRepository;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrgConfig {

    @Bean
    public ModelMapper modelMapper(){
        ModelMapper modelMapper = new ModelMapper();
        //modelMapper.getConfiguration().setSkipNullEnabled(true);

        //  Strict matching to avoid ambiguity
        modelMapper.getConfiguration()
                .setSkipNullEnabled(true)
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(org.modelmapper.config.Configuration.AccessLevel.PRIVATE);


        return modelMapper;
    }

    //@Bean
    CommandLineRunner seedOrganizations(
            OrganizationUnitRepository repository) {

        return args -> {

            OrganizationUnit party = new OrganizationUnit();
            party.setName("My Party");
            party.setType(OrganizationType.PARTY);
            repository.save(party);

            OrganizationUnit maharashtra =
                    new OrganizationUnit();

            maharashtra.setName("Maharashtra");
            maharashtra.setType(
                    OrganizationType.STATE);
            maharashtra.setParent(party);

            repository.save(maharashtra);

            OrganizationUnit pune =
                    new OrganizationUnit();

            pune.setName("Pune");
            pune.setType(
                    OrganizationType.DISTRICT);
            pune.setParent(maharashtra);

            repository.save(pune);
        };
    }
}
