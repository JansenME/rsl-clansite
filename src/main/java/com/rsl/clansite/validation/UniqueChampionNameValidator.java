package com.rsl.clansite.validation;

import com.rsl.clansite.repository.ChampionRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

public class UniqueChampionNameValidator implements ConstraintValidator<UniqueChampionName, String> {
    private final ChampionRepository championRepository;

    @Autowired
    public UniqueChampionNameValidator(ChampionRepository championRepository) {
        this.championRepository = championRepository;
    }

    @Override
    public boolean isValid(String name, ConstraintValidatorContext context) {
        if (name == null || name.isBlank()) {
            return true;
        }

        return !championRepository.existsByName(name);
    }
}
