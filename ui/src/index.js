/*
 * Plugin "vm-vcloud" — VMware vCloud Director implementation of plugin-vm.
 *
 * Tool-level plugin (`service:vm:vcloud`). Augments the parent `plugin-vm`
 * via i18n parameter labels + row features (vDirector org link,
 * instance-id chip) merged in through plugin-vm's `subPluginIdFor`
 * delegation hook.
 *
 * Authored as source — compiled to `/main/vm-vcloud/vue/index.js` by Vite.
 */
import { useI18nStore } from '@ligoj/host'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'

const features = {
  renderFeatures: service.renderFeatures,
  renderDetailsKey: service.renderDetailsKey,
}

export default {
  id: 'vm-vcloud',
  label: 'VM vCloud',
  requires: ['vm'],
  install() {
    const i18n = useI18nStore()
    i18n.merge(enMessages, 'en')
    i18n.merge(frMessages, 'fr')
  },
  feature(action, ...args) {
    const fn = features[action]
    if (!fn) throw new Error(`Plugin "vm-vcloud" has no feature "${action}"`)
    return fn(...args)
  },
  service,
  meta: { icon: 'mdi-cloud', color: 'indigo-darken-1' },
}

export { service }
