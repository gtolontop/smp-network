import OpenAI from 'openai';

import { config } from '../config.js';
import { child } from '../utils/logger.js';

import { AI_TOOLS, runTool } from './tools.js';

const log = child({ mod: 'ai' });

export const glm = new OpenAI({
  baseURL: config.ai.baseUrl,
  apiKey: config.ai.apiKey || 'missing',
});

const SYSTEM_PROMPT = [
  "Tu es l'assistant du serveur Minecraft SMP. Tu parles français, direct, sec, sans émoji inutiles.",
  "Quand la question porte sur le serveur, appelle les outils fournis au lieu d'inventer.",
  "Si tu n'as pas les données et qu'aucun outil ne peut les obtenir, dis-le franchement.",
  "Garde tes réponses courtes (1-6 phrases) sauf si on te demande un détail complet.",
  "Tu n'exécutes jamais de commande modératrice ou destructive ; si un utilisateur la demande, renvoie vers la bonne commande slash.",
].join('\n');

export interface AskArgs {
  prompt: string;
  userName?: string;
  extraContext?: string;
}

export async function ask(args: AskArgs): Promise<string> {
  if (!config.ai.apiKey) {
    return "L'IA n'est pas configurée (AI_API_KEY manquante).";
  }

  const messages: OpenAI.Chat.ChatCompletionMessageParam[] = [
    { role: 'system', content: SYSTEM_PROMPT },
  ];
  if (args.extraContext) {
    messages.push({ role: 'system', content: `Contexte additionnel:\n${args.extraContext}` });
  }
  messages.push({
    role: 'user',
    content: args.userName ? `[${args.userName}] ${args.prompt}` : args.prompt,
  });

  for (let step = 0; step < 4; step++) {
    const completion = await glm.chat.completions.create({
      model: config.ai.model,
      messages,
      tools: AI_TOOLS,
      tool_choice: 'auto',
      temperature: 0.6,
    });

    const choice = completion.choices[0];
    if (!choice) return 'Pas de réponse.';

    const msg = choice.message;
    if (msg.tool_calls?.length) {
      messages.push({
        role: 'assistant',
        content: msg.content ?? '',
        tool_calls: msg.tool_calls,
      });
      for (const call of msg.tool_calls) {
        if (call.type !== 'function') continue;
        let parsed: Record<string, unknown> = {};
        try {
          parsed = JSON.parse(call.function.arguments || '{}');
        } catch {
          parsed = {};
        }
        const result = await runTool(call.function.name, parsed);
        log.debug({ tool: call.function.name, args: parsed }, 'ai tool call');
        messages.push({
          role: 'tool',
          tool_call_id: call.id,
          content: JSON.stringify(result ?? null),
        });
      }
      continue;
    }

    return msg.content?.trim() || 'Pas de réponse.';
  }
  return "Trop d'allers-retours — réponse abandonnée.";
}
