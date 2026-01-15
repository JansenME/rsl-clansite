package com.rsl.clansite.model.entity;

import com.rsl.clansite.model.enums.ConditionCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "siege_conditions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
        @CompoundIndex(name = "category_key_idx", def = "{'category': 1, 'conditionKey': 1}", unique = true)
})
public class SiegeConditionEntity {
    @Id
    private ObjectId id;

    private ConditionCategory category;
    private String conditionKey;
    private boolean isActive = false;

    public SiegeConditionEntity(ConditionCategory category, String conditionKey) {
        this.category = category;
        this.conditionKey = conditionKey;
        this.isActive = false;
    }
}