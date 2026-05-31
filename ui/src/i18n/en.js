// vCloud parameter labels for the vm-vcloud tool plugin. Keys mirror the
// plugin's real Java parameters (VCloudPluginResource:
// api/url/user/password/organization/id). Flat keys so
// `service:vm:vcloud:*` ids resolve as literal lookups.
export default {
  'service:vm:vcloud:api': 'API URL',
  'service:vm:vcloud:url': 'vDirector URL',
  'service:vm:vcloud:user': 'User',
  'service:vm:vcloud:password': 'Password',
  'service:vm:vcloud:organization': 'Organization',
  'service:vm:vcloud:id': 'VM Identifier',
  'service:vm:vcloud:console': 'Console',
}
