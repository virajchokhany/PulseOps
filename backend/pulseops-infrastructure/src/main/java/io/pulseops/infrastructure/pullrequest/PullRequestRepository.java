package io.pulseops.infrastructure.pullrequest;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PullRequestRepository extends JpaRepository<PullRequestEntity, Long> {

    /** Pull requests shipped by the given deployments, matched on commit SHA. */
    List<PullRequestEntity> findByCommitShaInOrderByMergedAtDesc(Collection<String> commitShas);

    List<PullRequestEntity> findByCommitSha(String commitSha);
}
