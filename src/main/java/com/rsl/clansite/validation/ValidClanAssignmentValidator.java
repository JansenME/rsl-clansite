package com.rsl.clansite.validation;

import com.rsl.clansite.model.dto.NewClanmemberDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidClanAssignmentValidator implements ConstraintValidator<ValidClanAssignment, NewClanmemberDTO> {
    @Override
    public boolean isValid(NewClanmemberDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        boolean rankMissing = dto.getClanRank() == null;
        boolean groupMissing = dto.getClanGroup() == null;

        if (!rankMissing && !groupMissing) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        if (rankMissing && groupMissing) {
            context.buildConstraintViolationWithTemplate("You must select both a Clan Rank and a Clan Group.")
                    .addConstraintViolation();
        } else if (rankMissing) {
            context.buildConstraintViolationWithTemplate("You must select a Clan Rank.")
                    .addPropertyNode("clanRank").addConstraintViolation();
        } else {
            context.buildConstraintViolationWithTemplate("You must select a Clan Group.")
                    .addPropertyNode("clanGroup").addConstraintViolation();
        }

        return false;
    }
}
