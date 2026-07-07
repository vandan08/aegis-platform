{{/* Common labels applied to every object. */}}
{{- define "aegis.labels" -}}
app.kubernetes.io/part-of: aegis
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{/* Selector labels for a given component (arg = component name). */}}
{{- define "aegis.selectorLabels" -}}
app.kubernetes.io/name: {{ . }}
app.kubernetes.io/instance: {{ . }}
{{- end -}}

{{/* Fully-qualified image reference. */}}
{{- define "aegis.image" -}}
{{- printf "%s/%s:%s" .root.Values.image.registry .name .root.Values.image.tag -}}
{{- end -}}
