import { Siren } from 'lucide-react';
import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useGoBack } from '../lib/useGoBack';
import { Button, ASSISTANT_NAME, HelpAssistantChat, assistantTicketDescription, SafetyText, TopHeader, useAppLanguage, useTranslation } from '@sheout/design-system';
import { assistantApi } from '../api/client';
import { localEmergencyNumber } from '../lib/emergency';

/**
 * SheOut Help for riders. Its SOS is the real SOS screen; its hand-off is
 * the ordinary Raise an issue form, pre-filled - see HelpAssistantChat.
 */
export function HelpAssistant() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const goBack = useGoBack('/help');
  const language = useAppLanguage();
  const emergency = useMemo(localEmergencyNumber, []);

  return (
    <div className="space-y-4">
      <TopHeader variant="back" title={ASSISTANT_NAME} onBack={goBack} />
      <HelpAssistantChat
        ask={(messages) => assistantApi.ask(messages, language)}
        emergencyNumber={emergency.number}
        sosAction={
          <Button fullWidth size="lg" variant="danger" icon={<Siren className="h-5 w-5" />} onClick={() => navigate('/sos')} data-testid="assistant-open-sos">
            <SafetyText k="safetyCenter.sos.open" englishClassName="font-normal" />
          </Button>
        }
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
