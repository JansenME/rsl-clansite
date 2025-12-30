package com.rsl.clansite.validation;

import com.rsl.clansite.repository.ClanmemberRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

public class UniqueIngameNameValidator implements ConstraintValidator<UniqueIngameName, String> {
    private final ClanmemberRepository clanmemberRepository;

    @Autowired
    public UniqueIngameNameValidator(ClanmemberRepository clanmemberRepository) {
        this.clanmemberRepository = clanmemberRepository;
    }

    @Override
    public boolean isValid(String ingameName, ConstraintValidatorContext context) {
        if (ingameName == null || ingameName.isBlank()) {
            return true;
        }

        return !clanmemberRepository.existsByIngameName(ingameName);
    }
}
