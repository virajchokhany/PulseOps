-- Deterministic demo evidence for the AI investigation.
--
-- Timestamps are RELATIVE to migration time so the "deployment happened just
-- before the incident" story holds no matter when you run the demo.

INSERT INTO services (name, display_name, owner, repository, source_path, environment, version, tier, description) VALUES
    ('order-service',   'Order Service',   'team-checkout', 'shopflow', 'backend/shopflow-order-service',   'prod', '2.1.0', 'CRITICAL',
     'Accepts customer orders and calls payment-service synchronously to authorise them.'),
    ('payment-service', 'Payment Service', 'team-payments', 'shopflow', 'backend/shopflow-payment-service', 'prod', '1.4.2', 'CRITICAL',
     'Authorises payments through an external payment provider.');

INSERT INTO service_dependencies (service_name, depends_on_name, description) VALUES
    ('order-service', 'payment-service', 'Synchronous HTTP call during order placement. A payment failure fails the order.');

INSERT INTO deployments (service_name, version, commit_sha, environment, deployed_by, deployed_at, status, notes) VALUES
    ('payment-service', '1.4.1', 'd41f8a2c9b7e4f1a8c3d5e6f7a8b9c0d1e2f3a4b', 'prod', 'team-payments', now() - INTERVAL '6 days',  'SUCCEEDED', 'Routine dependency bump.'),
    ('payment-service', '1.4.2', 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678', 'prod', 'team-payments', now() - INTERVAL '30 minutes', 'SUCCEEDED', 'Payment provider timeout configuration change.'),
    ('order-service',   '2.1.0', 'f0e9d8c7b6a5948372615049382716f5e4d3c2b1', 'prod', 'team-checkout', now() - INTERVAL '4 days',  'SUCCEEDED', 'Checkout validation improvements.');

INSERT INTO pull_requests (number, repository, title, description, author, commit_sha, merged_at, url) VALUES
    (482, 'shopflow', 'Increase payment provider timeout',
     'Raises the outbound payment provider read timeout from 800ms to 5s and removes the previous retry budget. Intended to reduce spurious failures during provider slow periods.',
     'priya.n', 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678', now() - INTERVAL '45 minutes', 'https://example.invalid/shopflow/pull/482'),
    (475, 'shopflow', 'Bump http client dependency',
     'Routine dependency update, no behaviour change.',
     'sam.k', 'd41f8a2c9b7e4f1a8c3d5e6f7a8b9c0d1e2f3a4b', now() - INTERVAL '6 days', 'https://example.invalid/shopflow/pull/475'),
    (469, 'shopflow', 'Improve checkout validation messages',
     'User-facing validation copy for the checkout flow.',
     'lena.r', 'f0e9d8c7b6a5948372615049382716f5e4d3c2b1', now() - INTERVAL '4 days', 'https://example.invalid/shopflow/pull/469');

INSERT INTO pull_request_files (pull_request_id, file_path, change_type, additions, deletions)
SELECT pr.id, f.file_path, f.change_type, f.additions, f.deletions
FROM pull_requests pr
JOIN (VALUES
    (482, 'backend/shopflow-payment-service/src/main/java/io/pulseops/shopflow/payment/client/PaymentProviderClient.java', 'MODIFIED', 18, 11),
    (482, 'backend/shopflow-payment-service/src/main/resources/application.yml',                                          'MODIFIED',  4,  2),
    (475, 'pom.xml',                                                                                                      'MODIFIED',  2,  2),
    (469, 'backend/shopflow-order-service/src/main/java/io/pulseops/shopflow/order/api/OrderController.java',              'MODIFIED',  9,  3)
) AS f (pr_number, file_path, change_type, additions, deletions)
  ON f.pr_number = pr.number
WHERE pr.repository = 'shopflow';

INSERT INTO runbooks (service_name, alert_type, title, content) VALUES
    ('payment-service', 'PaymentHighErrorRate', 'Payment 5xx spike',
     E'1. Check whether a payment-service deployment landed in the last hour; if so, roll back first and investigate after.\n'
     '2. Confirm whether failures are provider timeouts or internal errors by inspecting the failure reason on recent telemetry.\n'
     '3. If the external provider is degraded, failures will be uniformly distributed across endpoints.\n'
     '4. Escalate to team-payments if the error rate stays above 20% for more than 15 minutes.'),
    ('payment-service', 'PaymentHighLatency', 'Payment latency above SLO',
     E'1. Payment authorisation is expected to complete within 800ms at p95.\n'
     '2. Latency above 2s almost always means the outbound provider call is waiting on its timeout.\n'
     '3. Verify the configured provider read timeout matches the documented 800ms budget.\n'
     '4. A raised timeout converts fast failures into slow failures and increases queueing.'),
    ('order-service', 'OrderFailureRate', 'Order failures',
     E'1. order-service fails an order when payment authorisation fails.\n'
     '2. Always check payment-service health before treating this as an order-service defect.');
