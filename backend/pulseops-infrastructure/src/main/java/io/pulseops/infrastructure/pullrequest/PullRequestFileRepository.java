package io.pulseops.infrastructure.pullrequest;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PullRequestFileRepository extends JpaRepository<PullRequestFileEntity, Long> {

    List<PullRequestFileEntity> findByPullRequestIdIn(Collection<Long> pullRequestIds);

    List<PullRequestFileEntity> findByPullRequestId(Long pullRequestId);
}
