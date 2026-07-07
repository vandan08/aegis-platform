# Unit tests for the Aegis authorization policy. Run with:  opa test policies/
package aegis.authz

import rego.v1

test_admin_allowed_to_do_anything if {
	allow with input as {
		"subject": {"id": "root", "roles": ["ADMIN"], "scopes": []},
		"action": "DELETE",
		"resource": {"path": "/api/demo/thing", "segments": ["api", "demo", "thing"]},
		"context": {"hour": 3},
	}
}

test_demo_read_allowed_with_scope if {
	allow with input as {
		"subject": {"id": "alice", "roles": ["USER"], "scopes": ["demo.read"]},
		"action": "GET",
		"resource": {"path": "/api/demo/whoami", "segments": ["api", "demo", "whoami"]},
		"context": {"hour": 12},
	}
}

test_demo_read_denied_without_scope if {
	not allow with input as {
		"subject": {"id": "alice", "roles": ["USER"], "scopes": []},
		"action": "GET",
		"resource": {"path": "/api/demo/whoami", "segments": ["api", "demo", "whoami"]},
		"context": {"hour": 12},
	}
}

test_owner_can_read_own_profile if {
	allow with input as {
		"subject": {"id": "alice", "roles": ["USER"], "scopes": []},
		"action": "GET",
		"resource": {"path": "/api/users/alice", "segments": ["api", "users", "alice"]},
		"context": {"hour": 12},
	}
}

test_non_owner_cannot_read_others_profile if {
	not allow with input as {
		"subject": {"id": "bob", "roles": ["USER"], "scopes": []},
		"action": "GET",
		"resource": {"path": "/api/users/alice", "segments": ["api", "users", "alice"]},
		"context": {"hour": 12},
	}
}

test_write_denied_outside_business_hours if {
	not allow with input as {
		"subject": {"id": "alice", "roles": ["USER"], "scopes": ["demo.write"]},
		"action": "POST",
		"resource": {"path": "/api/demo/thing", "segments": ["api", "demo", "thing"]},
		"context": {"hour": 22},
	}
}

test_write_allowed_in_business_hours if {
	allow with input as {
		"subject": {"id": "alice", "roles": ["USER"], "scopes": ["demo.write"]},
		"action": "POST",
		"resource": {"path": "/api/demo/thing", "segments": ["api", "demo", "thing"]},
		"context": {"hour": 10},
	}
}

test_read_scope_cannot_escalate_to_write if {
	# A caller holding only demo.read must not be able to perform a write, even in business hours.
	not allow with input as {
		"subject": {"id": "alice", "roles": ["USER"], "scopes": ["demo.read"]},
		"action": "POST",
		"resource": {"path": "/api/demo/thing", "segments": ["api", "demo", "thing"]},
		"context": {"hour": 10},
	}
}

test_anonymous_denied_by_default if {
	not allow with input as {
		"subject": {"id": "", "roles": [], "scopes": []},
		"action": "GET",
		"resource": {"path": "/api/demo/whoami", "segments": ["api", "demo", "whoami"]},
		"context": {"hour": 12},
	}
}
