import { classNames } from '../lib/classNames'
import styles from './Settings.module.css'
import { useResource, useSession } from '../hooks'
import { usePreferences } from '../lib/preferences'
import type { Settings as UserSettings } from '../lib/types'
import Appearance from '../components/settings/Appearance'
import SettingsForm from '../components/settings/SettingsForm'
import SettingsNavigation from '../components/settings/SettingsNavigation'
import { Icon, Notice, PageHeading, ResourceState } from '../components/ui'

export default function Settings() {
  const { t } = usePreferences()
  const session = useSession()
  const resource = useResource<UserSettings>(
    session ? '/users/me/settings' : null,
  )
  return (
    <>
      <PageHeading title="settings" description="settingsDescription" />
      <div className={styles['settings-layout']}>
        <SettingsNavigation analysisAvailable={!!resource.data} />
        <div className={classNames('stack', styles['settings-content'])}>
          <Appearance />
          {!session && <Notice>{t('localSettings')}</Notice>}
          <ResourceState
            loading={resource.loading}
            error={resource.error}
            retry={resource.reload}
          />
          {resource.data && (
            <SettingsForm
              key={resource.data.updateTime || 'defaults'}
              initial={resource.data}
            />
          )}
          <section id="settings-data" tabIndex={-1} aria-labelledby="data-heading" className={classNames('card', styles['settings-notice'])}>
            <Icon name="shield" size={28} />
            <h2 id="data-heading">{t('dataNotice')}</h2>
            <p>{t('privacy')}</p>
            <hr />
            <p>{t('reportLanguageHint')}</p>
          </section>
        </div>
      </div>
    </>
  )
}
