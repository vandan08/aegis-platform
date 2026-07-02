# Aegis fine-grained authorization policy — the Policy Decision Point (PDP).
#
# The gateway (Policy Enforcement Point) queries this policy at
#   POST /v1/data/aegis/authz/allow
# with an `input` document shaped like:
#
#   input.subject  = { "id": "alice", "roles": ["USER"], "scopes": ["demo.read"], "tenant": "acme" }
#   input.action   = "GET" | "POST" | "PUT" | "PATCH" | "DELETE"    (the HTTP method)
#   input.resource = { "path": "/api/demo/whoami", "segments": ["api","demo","whoami"] }
#   input.context  = { "ip": "203.0.113.7", "hour": 14 }            (hour is 0-23, UTC)
#
# The policy is deliberately fail-closed: `allow` defaults to false and a request is
# permitted only if some rule below explicitly grants it. It demonstrates both RBAC
# (roles/scopes) and ABAC (resource ownership, time-of-day).
package aegis.authz

import rego.v1

default allow := false

# --- RBAC -------------------------------------------------------------------

# Admins may perform any action on any resource.
allow if "ADMIN" in input.subject.roles

# Reading the demo API requires the `demo.read` scope.
allow if {
	input.action == "GET"
	startswith(input.resource.path, "/api/demo/")
	"demo.read" in input.subject.scopes
}

# --- ABAC: resource ownership ----------------------------------------------

# A user may act on their own profile: /api/users/{id} where {id} == subject id.
allow if {
	input.resource.segments[0] == "api"
	input.resource.segments[1] == "users"
	input.resource.segments[2] == input.subject.id
}

# --- ABAC: time-of-day ------------------------------------------------------

# Writes to the demo API require the `demo.write` scope AND must happen during
# business hours (09:00–17:00 UTC). Admins bypass this via the RBAC rule above.
allow if {
	input.action in {"POST", "PUT", "PATCH", "DELETE"}
	startswith(input.resource.path, "/api/demo/")
	"demo.write" in input.subject.scopes
	input.context.hour >= 9
	input.context.hour < 17
}

# Convenience: expose the reason set for debugging / audit (not used for the decision).
reasons contains "admin" if "ADMIN" in input.subject.roles

reasons contains "demo-read-scope" if {
	input.action == "GET"
	"demo.read" in input.subject.scopes
}
