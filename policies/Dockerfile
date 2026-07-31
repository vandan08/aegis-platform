# syntax=docker/dockerfile:1
# The Policy Decision Point, with the policies baked into the image.
#
# In local development OPA reads ./policies through a bind mount, which is convenient but
# not deployable: there is no host directory to mount in a managed container platform, and
# a policy that can change under a running PDP is a policy nobody can audit. Building the
# rules into an immutable, tagged image makes the deployed authorization behaviour exactly
# as reviewable as the code — the same commit produces the same decisions.
FROM openpolicyagent/opa:1.0.0

# Rego sources plus their tests. The tests ride along deliberately: `opa test /policies`
# can then be run against the very image that is serving decisions, not a copy of it.
COPY --chown=1000:1000 *.rego /policies/

USER 1000
EXPOSE 8181

# Fixed port rather than $PORT: this base image has no shell to expand it (exec form only).
# Deployments that inject a port instead set PORT=8181 for this service — see render.yaml.
ENTRYPOINT ["/opa"]
CMD ["run", "--server", "--addr=0.0.0.0:8181", "--log-level=info", "/policies"]
