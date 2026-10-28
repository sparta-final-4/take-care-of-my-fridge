package org.example.takecareofmyfridgebe.domain.review.scheduler;

import org.example.takecareofmyfridgebe.domain.review.facade.ReviewSummaryFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReviewSummarySchedulerConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReviewSummaryScheduler.class)
            .withBean(ReviewSummaryFacade.class, () -> mock(ReviewSummaryFacade.class));

    @Test
    @DisplayName("app.review-summary.enabled=false이면 ReviewSummaryScheduler 빈이 등록되지 않는다")
    void schedulerNotRegisteredWhenDisabled() {
        contextRunner.withPropertyValues("app.review-summary.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ReviewSummaryScheduler.class));
    }

    @Test
    @DisplayName("app.review-summary.enabled=true이면 ReviewSummaryScheduler 빈이 등록된다")
    void schedulerRegisteredWhenEnabled() {
        contextRunner.withPropertyValues("app.review-summary.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ReviewSummaryScheduler.class));
    }
}
