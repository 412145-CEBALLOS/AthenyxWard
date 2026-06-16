import { EmailAnalysisResult } from '../models/email-analysis.model';
import { EmailDetail } from '../models/email-summary.model';

export function computeMockAnalysis(detail: EmailDetail): EmailAnalysisResult {
  const senderDomain = (detail.sender.split('@')[1] ?? '').toLowerCase();
  const displayName = detail.senderName?.trim() || detail.sender;
  const displayMismatch = !!detail.senderName &&
    detail.senderName.length > 0 &&
    !detail.sender.toLowerCase().includes(displayName.toLowerCase().split(' ')[0] ?? '__');
  const riskPercentage = displayMismatch || senderDomain.includes('verify') ? 78 : 22;
  const riskLevel: EmailAnalysisResult['riskLevel'] =
    riskPercentage < 40 ? 'GREEN' : riskPercentage < 70 ? 'YELLOW' : 'RED';

  return {
    analysisId: detail.id,
    emailId: detail.id,
    riskPercentage,
    riskLevel,
    threatCategories: riskLevel === 'GREEN'
      ? []
      : ['PHISHING', 'DANGEROUS_LINK', 'SOCIAL_ENGINEERING'],
    heuristicFindings: [
      { rule: 'urgent-language', description: 'Tono de urgencia artificial detectado', score: 25 },
      { rule: 'domain-mismatch', description: 'Dominio del remitente no coincide con la marca', score: 30 },
    ],
    suspiciousUrls: riskLevel === 'GREEN' ? [] : [
      {
        raw: 'http://banc0-verify.example/login',
        resolvedDomain: 'banc0-verify.example',
        reason: 'Dominio no oficial del banco declarado.',
      },
    ],
    senderTrust: {
      sender: detail.sender,
      displayName,
      domain: senderDomain,
      displayMismatch,
      spf: riskLevel === 'GREEN' ? 'PASS' : 'FAIL',
      dkim: riskLevel === 'GREEN' ? 'PASS' : 'NEUTRAL',
    },
    aiExplanation: riskLevel === 'GREEN'
      ? 'El correo proviene de un remitente conocido y no presenta indicadores de riesgo.'
      : 'El correo simula ser una notificación urgente de un banco, solicita verificar identidad mediante un enlace externo y genera presión temporal para forzar la acción.',
    contentSummary: detail.snippet || 'Sin resumen disponible.',
    recommendedActions: riskLevel === 'GREEN'
      ? [{ label: 'No se requieren acciones especiales.' }]
      : [
          { label: 'No hacer clic en los enlaces del correo.' },
          { label: 'Contactar al banco por canales oficiales.' },
          { label: 'Marcar como phishing y eliminar.' },
        ],
    analyzedAt: new Date().toISOString(),
    source: 'HYBRID',
    modelName: 'llama3',
  };
}
