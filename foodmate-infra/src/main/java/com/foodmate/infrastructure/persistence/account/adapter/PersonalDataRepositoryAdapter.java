package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.PersonalDataRepository;
import com.foodmate.infrastructure.persistence.account.PersonalDataMapper;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class PersonalDataRepositoryAdapter extends MapperRepositoryAdapter<PersonalDataRepository> {
    public PersonalDataRepositoryAdapter(PersonalDataMapper mapper) {
        super(mapper, PersonalDataRepository.class);
    }
}
