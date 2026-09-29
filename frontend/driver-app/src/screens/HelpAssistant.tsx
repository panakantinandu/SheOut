import { useNavigate } from 'react-router-dom';
import { useGoBack } from '../lib/useGoBack';
import { ASSISTANT_NAME, HelpAssistantChat, assistantTicketDescription, TopHeader, useAppLanguage, useTranslation } from '@sheout/design-system';
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
  const goBack = useGoBack('/help');
  const language = useAppLanguage();

  return (
    <div className="space-y-4">
      <TopHeader variant="back" title={ASSISTANT_NAME} onBack={goBack} />
      <HelpAssistantChat
        ask={(messages) => assistantApi.ask(messages, language)}
        emergencyNumber="112"
        sosAction={<PartnerSos />}
        stillAvatar
        onRaiseTicket={(draft, conversation) =>
          navigate('/help/new', {
            state: {
              prefill: {
                category: draft.category,
                subject: draft.subject,
                description: assistantTicketDescription(
                  draft.summary,
                  conversation,
                  t('help.conversationHeading'),
                  t('help.fromAssistant', { name: ASSISTANT_NAME })
                ),
              },
            },
          })
        }
      />
    </div>
  );
}
