/*
 * Contract tests for plugin-vm-vcloud, incl. the parent -> child
 * delegation: when vm-vcloud is registered, plugin-vm's renderFeatures
 * appends the vDirector org link for a vcloud node.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { pluginRegistry, useI18nStore } from '@ligoj/host'
import def from '../index.js'
import pluginVmDef from '../../../../plugin-vm/ui/src/index.js'

beforeEach(() => { setActivePinia(createPinia()) })

describe('plugin-vm-vcloud manifest', () => {
  it('exposes a valid tool-level manifest', () => {
    expect(def.id).toBe('vm-vcloud')
    expect(def.requires).toEqual(['vm'])
    expect(def.routes).toBeUndefined()
    expect(typeof def.install).toBe('function')
    expect(typeof def.feature).toBe('function')
    expect(def.service).toBeTypeOf('object')
    expect(def.meta).toMatchObject({ icon: expect.any(String), color: expect.any(String) })
  })

  it('merges i18n on install', () => {
    const i18n = useI18nStore()
    def.install()
    expect(i18n.t('service:vm:vcloud:organization')).toBe('Organization')
    expect(i18n.t('service:vm:vcloud:api')).toBe('API URL')
  })

  it('throws for an unknown feature', () => {
    expect(() => def.feature('nope')).toThrow(/no feature "nope"/)
  })

  it('renderFeatures returns the vDirector org link when url + organization are set', () => {
    def.install()
    const vnodes = def.feature('renderFeatures', {
      node: { id: 'service:vm:vcloud:vm1' },
      parameters: { 'service:vm:vcloud:url': 'https://vcd.example.org', 'service:vm:vcloud:organization': 'Acme' },
    })
    expect(vnodes).toHaveLength(1)
    expect(vnodes[0].__v_isVNode).toBe(true)
    expect(vnodes[0].props.href).toBe('https://vcd.example.org/Acme/#/vmListPage?')
    expect(vnodes[0].props.target).toBe('_blank')
  })

  it('renderFeatures returns an empty list without url or organization', () => {
    def.install()
    expect(def.feature('renderFeatures', { parameters: { 'service:vm:vcloud:url': 'https://x' } })).toEqual([])
    expect(def.feature('renderFeatures', {})).toEqual([])
  })

  it('renderDetailsKey returns the instance chip when present', () => {
    def.install()
    expect(def.feature('renderDetailsKey', { parameters: { 'service:vm:vcloud:id': '42' } })).toBeTruthy()
    expect(def.feature('renderDetailsKey', { parameters: {} })).toBeNull()
  })
})

describe('plugin-vm -> plugin-vm-vcloud delegation', () => {
  beforeEach(() => {
    pluginVmDef.install({ router: { addRoute() {} } })
    def.install()
    pluginRegistry.register('vm-vcloud', def)
  })
  afterEach(() => { pluginRegistry.remove('vm-vcloud') })

  it('appends the vDirector org link to plugin-vm renderFeatures output', () => {
    const result = pluginVmDef.feature('renderFeatures', {
      id: 9,
      node: { id: 'service:vm:vcloud:vm1' },
      data: {},
      parameters: { 'service:vm:vcloud:url': 'https://vcd.example.org', 'service:vm:vcloud:organization': 'Acme' },
    })
    expect(result.length).toBe(2)
    for (const node of result) expect(node.__v_isVNode).toBe(true)
  })

  it('does not delegate for a non-vcloud tool', () => {
    const result = pluginVmDef.feature('renderFeatures', {
      id: 9,
      node: { id: 'service:vm:other:vm1' },
      data: {},
      parameters: {},
    })
    expect(result.length).toBe(1)
  })
})
