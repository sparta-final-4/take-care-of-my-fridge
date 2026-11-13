package org.example.takecareofmyfridgebe.domain.chat.reply;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates.EmptyReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoRecipeReplyWriterTest {

    @Test
    @DisplayName("말한 요리를 못 찾았으면 요리 이름을 쓴 그대로(공백 포함) 넣어 아직 없다고 쓴다")
    void writesNotFound() {
        assertThat(NoRecipeReplyWriter.write(EmptyReason.notFound("나가사키 짬뽕")))
                .isEqualTo("나가사키 짬뽕 레시피는 아직 없어요. 다른 요리 이름이나 갖고 있는 재료로 물어봐 주세요.");
    }

    @Test
    @DisplayName("뺄 재료 때문에 뺐으면 재료를 쉼표로 잇고 마지막 재료 받침에 맞춰 이/가를 붙인다")
    void writesExcluded_withIngredientNames() {
        assertThat(NoRecipeReplyWriter.write(EmptyReason.excluded("새우볶음밥", List.of("새우"))))
                .isEqualTo("새우볶음밥에는 새우가 들어 있어서 추천에서 뺐어요. 다른 요리를 물어봐 주세요.");
        assertThat(NoRecipeReplyWriter.write(EmptyReason.excluded("새우볶음밥", List.of("새우", "땅콩"))))
                .isEqualTo("새우볶음밥에는 새우, 땅콩이 들어 있어서 추천에서 뺐어요. 다른 요리를 물어봐 주세요.");
    }

    @Test
    @DisplayName("뺄 재료 이름을 모르면 못 드시는 재료라고 쓴다")
    void writesExcluded_whenIngredientNamesUnknown() {
        assertThat(NoRecipeReplyWriter.write(EmptyReason.excluded("새우볶음밥", List.of())))
                .isEqualTo("새우볶음밥에는 못 드시는 재료가 들어 있어서 추천에서 뺐어요. 다른 요리를 물어봐 주세요.");
    }
}
