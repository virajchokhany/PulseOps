package io.pulseops.worker.investigation.evidence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.pulseops.domain.evidence.PullRequestView;
import io.pulseops.infrastructure.deployment.DeploymentEntity;
import io.pulseops.infrastructure.deployment.DeploymentRepository;
import io.pulseops.infrastructure.pullrequest.PullRequestEntity;
import io.pulseops.infrastructure.pullrequest.PullRequestFileEntity;
import io.pulseops.infrastructure.pullrequest.PullRequestFileRepository;
import io.pulseops.infrastructure.pullrequest.PullRequestRepository;

/** Resolves the pull requests carried by recent deployments, joined on commit SHA. */
@Component
public class PullRequestEvidenceProvider implements EvidenceProvider<List<PullRequestView>> {

    private static final Duration LOOKBACK = Duration.ofHours(24);

    private final DeploymentRepository deploymentRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestFileRepository fileRepository;

    public PullRequestEvidenceProvider(DeploymentRepository deploymentRepository,
                                       PullRequestRepository pullRequestRepository,
                                       PullRequestFileRepository fileRepository) {
        this.deploymentRepository = deploymentRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.fileRepository = fileRepository;
    }

    @Override
    public String name() {
        return "pullRequests";
    }

    @Override
    @Transactional(readOnly = true)
    public List<PullRequestView> collect(EvidenceScope scope) {
        List<String> commitShas = deploymentRepository
                .findByServiceNameInAndDeployedAtAfterOrderByDeployedAtDesc(
                        scope.affectedServices(), scope.incidentOpenedAt().minus(LOOKBACK))
                .stream()
                .map(DeploymentEntity::getCommitSha)
                .distinct()
                .toList();

        if (commitShas.isEmpty()) {
            return List.of();
        }

        List<PullRequestEntity> pullRequests =
                pullRequestRepository.findByCommitShaInOrderByMergedAtDesc(commitShas);
        if (pullRequests.isEmpty()) {
            return List.of();
        }

        Map<Long, List<String>> filesByPr = fileRepository
                .findByPullRequestIdIn(pullRequests.stream().map(PullRequestEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        PullRequestFileEntity::getPullRequestId,
                        Collectors.mapping(PullRequestFileEntity::getFilePath, Collectors.toList())));

        List<PullRequestView> views = new ArrayList<>();
        for (PullRequestEntity pr : pullRequests) {
            views.add(new PullRequestView(
                    pr.getNumber(), pr.getRepository(), pr.getTitle(), pr.getDescription(),
                    pr.getAuthor(), pr.getCommitSha(), pr.getMergedAt(), pr.getUrl(),
                    filesByPr.getOrDefault(pr.getId(), List.of())));
        }
        return views;
    }
}
