/*
 * Service layer for plugin "vm-vcloud".
 *
 * Tool-level plugin (lives at `service:vm:vcloud`). The parent `plugin-vm`
 * delegates the subscription-row hooks to us via its `subPluginIdFor`
 * delegation. Mirrors the legacy vcloud controller:
 *
 *   - renderFeatures   -> a vDirector "home" link, built from the console
 *     URL + organization (`url + '/' + organization + '/#/vmListPage?'`),
 *     shown only when both are set. The legacy live-console popover (a
 *     PNG screenshot behind `subscription.parameters.console`) reads
 *     runtime data and is omitted here, like other live-data carousels.
 *   - renderDetailsKey -> the instance-id chip (`service:vm:vcloud:id`).
 *
 * Kept free of Vue SFC imports so it can be unit-tested without a DOM.
 */
import { renderServiceLink, renderDetailsChip, useI18nStore } from '@ligoj/host'

const PARAM_URL = 'service:vm:vcloud:url'
const PARAM_ORG = 'service:vm:vcloud:organization'
const PARAM_ID = 'service:vm:vcloud:id'

/** vDirector org "home" link. Mirrors the legacy renderServicelink. */
function renderFeatures(subscription) {
  const params = subscription?.parameters
  const url = params?.[PARAM_URL]
  const org = params?.[PARAM_ORG]
  if (!url || !org) return []
  const { t } = useI18nStore()
  return [
    renderServiceLink({
      icon: 'mdi-cloud',
      href: `${url.replace(/\/$/, '')}/${encodeURIComponent(org)}/#/vmListPage?`,
      title: t('service:vm:vcloud:console'),
    }),
  ]
}

/** Instance-id chip. Mirrors the legacy renderKey('service:vm:vcloud:id'). */
function renderDetailsKey(subscription) {
  const id = subscription?.parameters?.[PARAM_ID]
  if (!id) return null
  const { t } = useI18nStore()
  return renderDetailsChip({ icon: 'mdi-cloud', text: id, title: t('service:vm:vcloud:id') })
}

export default { renderFeatures, renderDetailsKey }
