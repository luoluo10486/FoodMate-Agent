package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.PersonalDataRepository;
import com.foodmate.infrastructure.persistence.account.PersonalDataMapper;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(PersonalDataMapper.class)
public class PersonalDataRepositoryAdapter extends MapperRepositoryAdapter<PersonalDataRepository> {
    public PersonalDataRepositoryAdapter(PersonalDataMapper mapper) {
        super(mapper, PersonalDataRepository.class);
    }
}
