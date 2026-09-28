import { useNavigate } from 'react-router-dom';
import { HelpAssistantChat, TopHeader, useAppLanguage, useTranslation } from '@sheout/design-system';
import { assistantApi } from '../api/client';
import { PartnerSos } from '../components/PartnerSos';

/**
 * SheOut Help for partners. Its SOS is the partner SOS (112 first, then
 * SheOut's safety team); its hand-off is the ordinary Raise an issue form,
 * pre-filled - see HelpAssistantChat.
 */
export function HelpAssistant() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const language = useAppLanguage();

  return (
    <div className="space-y-4">
      <TopHeader variant="back" title={t('help.assistantTitle')} onBack={() => navigate(-1)} />
      <HelpAssistantChat
        ask={(messages) => assistantApi.ask(messages, language)}
        emergencyNumber="112"
        sosAction={<PartnerSos />}
        onRaiseTicket={(draft) =>
          navigate('/help/new', {
            state: {
              prefill: {
                category: draft.category,
                subject: draft.subject,
                description: `${draft.summary}\n\n${t('help.fromAssistant')}`,
              },
            },
          })
        }
      />
    </div>
  );
}
