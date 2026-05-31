# plugin-vm-vcloud — Vue UI

Tool plugin (`service:vm:vcloud`), the VMware vCloud Director
implementation of the `vm` service. Compiled to `webjars/vm-vcloud/vue/`.

Ships i18n parameter labels (`service:vm:vcloud:*`) + `renderFeatures`
(vDirector org "home" link `url/<org>/#/vmListPage?`, shown when URL +
organization are set) + `renderDetailsKey` (instance-id chip). Declares
`requires: ['vm']`; the parent `plugin-vm` merges the row features via its
delegation hook. The legacy live-console PNG popover is omitted (runtime
data).

```bash
npm install && npm run build && npm run lint && npm test
```
