import type { Metadata } from 'next';
import Link from '@/components/site-link';
import { notFound } from 'next/navigation';
import { ExternalLink, Mail, Server, ShieldCheck } from 'lucide-react';
import { CopyServerButton } from '@/components/copy-server-button';
import { InfoHeader } from '@/components/info-header';
import { LiveServerStatus } from '@/components/live-server-status';
import { SiteFooter } from '@/components/site-footer';

type PageSection = { title: string; paragraphs?: string[]; bullets?: string[] };
type PageContent = { title: string; eyebrow: string; intro: string; updated?: string; sections: PageSection[] };

const pages: Record<string, PageContent> = {
  terms: {
    title: 'Termos de utilização', eyebrow: 'Utilização justa e responsável',
    intro: 'Estes termos explicam como podes usar o website, o servidor Minecraft e os serviços digitais do MerelyMeSMP.', updated: '30 de agosto de 2026',
    sections: [
      { title: 'Quem somos', paragraphs: ['O MerelyMeSMP é um servidor independente de Minecraft e um serviço digital operado a partir de Portugal. Questões podem ser enviadas para support@merelymesmp.com.', 'O MerelyMeSMP não é um produto oficial de Minecraft e não é aprovado nem associado à Mojang ou Microsoft.'] },
      { title: 'Acesso ao servidor', bullets: ['Segue as regras publicadas e as instruções razoáveis da equipa.', 'Não ataques o servidor, explores falhas ou tentes aceder a contas, sistemas ou dados de terceiros.', 'O acesso pode ser limitado para proteger jogadores, serviço e comunidade.'] },
      { title: 'Compras digitais', paragraphs: ['Ranks, pontos e outras vantagens digitais são opcionais. O preço e a descrição apresentados no checkout da Tip4Serv prevalecem no momento da compra.', 'Os artigos são normalmente entregues automaticamente ao nome Minecraft indicado. Em caso de falha, contacta o suporte com a referência da encomenda. Nunca envies passwords ou dados completos de pagamento.'] },
      { title: 'Alterações e disponibilidade', paragraphs: ['O conteúdo, eventos e equilíbrio do jogo podem mudar. Tentamos anunciar mudanças relevantes com antecedência, mas podemos agir imediatamente para proteger a segurança e estabilidade.', 'Nenhum serviço online garante disponibilidade ininterrupta. O histórico público distingue interrupções reais de períodos ainda sem dados suficientes.'] },
    ],
  },
  privacy: {
    title: 'Política de privacidade', eyebrow: 'Só os dados necessários',
    intro: 'Explicamos os dados usados para operar o website, responder ao suporte e proteger a comunidade.', updated: '30 de agosto de 2026',
    sections: [
      { title: 'Dados tratados', bullets: ['Nome Minecraft e identificador público quando o servidor o apresenta nas verificações públicas.', 'Email, nome Minecraft e conteúdo enviados voluntariamente através do suporte.', 'Referência de encomenda quando pedes ajuda com uma compra.', 'Contagens agregadas de ações como copiar o IP ou abrir a loja, sem cookies publicitários nem perfis comportamentais.'] },
      { title: 'Finalidades', paragraphs: ['Usamos estes dados para disponibilizar o serviço, responder a pedidos, investigar denúncias, resolver compras e medir quais páginas ajudam os jogadores. Não vendemos dados pessoais.'] },
      { title: 'Conservação e partilha', paragraphs: ['Os pedidos de suporte são guardados apenas durante o período necessário para os resolver, manter registos de segurança e cumprir obrigações legais. Dados podem ser processados por fornecedores técnicos como alojamento e checkout, dentro das funções respetivas.'] },
      { title: 'Os teus direitos', paragraphs: ['Podes pedir acesso, correção ou eliminação aplicável através de support@merelymesmp.com. Poderemos pedir informação suficiente para confirmar que o pedido diz respeito à pessoa certa.', 'Se fores menor, fala com um pai ou responsável antes de enviar informação pessoal.'] },
    ],
  },
  refunds: {
    title: 'Política de reembolsos', eyebrow: 'Suporte de compras',
    intro: 'Tratamos entregas falhadas, duplicadas ou incorretas de forma justa e respeitamos os direitos obrigatórios do consumidor.', updated: '30 de agosto de 2026',
    sections: [
      { title: 'Pede ajuda primeiro', paragraphs: ['Envia um pedido com a referência, nome Minecraft e descrição curta. Não envies passwords, números de cartão ou códigos de segurança.'], bullets: ['Cobrança ou encomenda duplicada.', 'Artigo não entregue após confirmação e prazo razoável.', 'Artigo materialmente diferente da descrição.', 'Compra não autorizada pelo titular, sujeita a verificação razoável.', 'Qualquer reembolso exigido pela lei aplicável.'] },
      { title: 'Conteúdo digital e livre resolução', paragraphs: ['Consumidores da UE têm normalmente 14 dias para resolver contratos à distância. Para conteúdo digital fornecido imediatamente, esse direito apenas pode terminar quando o consumidor pede expressamente o fornecimento imediato e reconhece a perda do direito, nos termos legais. Cada pedido é analisado com os registos do checkout e entrega.'] },
      { title: 'Abuso e chargebacks', paragraphs: ['Fraude, abuso deliberado de chargebacks ou retenção de benefícios já reembolsados pode resultar na remoção desses benefícios e restrições. Isto não limita o direito genuíno de contestar um pagamento incorreto ou não autorizado.'] },
    ],
  },
  rules: {
    title: 'Regras do servidor', eyebrow: 'Compete a sério. Joga de forma justa.',
    intro: 'Estas regras aplicam-se dentro do jogo e nos espaços oficiais da comunidade MerelyMeSMP.', updated: '30 de agosto de 2026',
    sections: [
      { title: 'Jogo justo', bullets: ['Proibidos clientes alterados, automação de combate, x-ray, macros com vantagem injusta ou modificações proibidas.', 'Não explores bugs, dupliques itens, contornes restrições ou escondas uma falha. Reporta problemas graves em privado.', 'Proibidos bots, ataques de negação de serviço, tráfego malicioso ou ações destinadas a prejudicar o desempenho.', 'Contas alternativas não podem evitar punições, manipular recompensas ou obter vantagem injusta.'] },
      { title: 'Respeito e segurança', bullets: ['Proibidas ameaças, assédio, discurso de ódio, conteúdo sexual, doxxing ou divulgação de informação privada.', 'Mantém nomes, skins, construções e mensagens adequados a um público que inclui menores.', 'Proibida a imitação de staff, ofertas enganosas, fraude, phishing ou pedidos de passwords e códigos.', 'Proibidos spam, publicidade perturbadora e comércio por dinheiro real fora da loja oficial.'] },
      { title: 'Moderação e recursos', paragraphs: ['A equipa pode aplicar avisos, restrições, rollbacks, mutes ou bans proporcionais à gravidade, histórico e risco.', 'Para recorrer, usa o suporte com nome Minecraft, data da punição e explicação concisa. Uma compra não influencia decisões de moderação.'] },
    ],
  },
  bans: {
    title: 'Bans e recursos', eyebrow: 'Moderação transparente',
    intro: 'Percebe como funcionam as punições e usa o canal oficial para recorrer de uma decisão.', updated: '31 de agosto de 2026',
    sections: [
      { title: 'Registos públicos de bans', paragraphs: ['A lista pública ainda não está ligada ao servidor. Só mostraremos registos verificados fornecidos por uma integração apenas de leitura; a ausência de um nome não prova que não existe uma punição.'], bullets: ['Campos públicos: nome Minecraft, categoria geral do motivo, data, validade quando aplicável e estado atual.', 'Endereços IP, email, notas da equipa, provas e detalhes de segurança nunca serão publicados.', 'Um registo pode ser adiado ou ocultado por segurança, privacidade ou investigação ativa.'] },
      { title: 'Recorrer de uma punição', paragraphs: ['Usa o centro de suporte com o teu nome Minecraft, a data aproximada da punição e uma explicação concisa. Nunca envies password, código de acesso ou token de autenticação.'], bullets: ['Envia um único recurso completo e aguarda a análise da equipa.', 'Inclui provas relevantes sem expor informação privada de outros jogadores.', 'As compras nunca influenciam decisões de moderação.'] },
      { title: 'Moderação justa', paragraphs: ['Avisos, mutes, restrições e bans devem ser proporcionais à gravidade, histórico e risco. Os registos públicos não devem expor dados pessoais sensíveis e podem ser ocultados por segurança, privacidade ou durante uma investigação.'] },
    ],
  },
  contact: {
    title: 'Contactar suporte', eyebrow: 'Estamos aqui para ajudar',
    intro: 'Usa o centro de suporte oficial para compras, problemas técnicos, privacidade, denúncias e recursos.',
    sections: [
      { title: 'Canal oficial', paragraphs: ['O formulário privado organiza o pedido e fornece uma referência. Também podes escrever para support@merelymesmp.com.', 'Nunca incluas password, número completo de cartão, acesso bancário, código de segurança ou token de autenticação.'] },
      { title: 'O que incluir', bullets: ['Compra: referência, nome Minecraft, produto e problema.', 'Problema técnico: nome, hora, Java Edition e mensagem de erro exata.', 'Recurso: nome, data da punição e explicação concisa.', 'Privacidade: pedido e informação suficiente para verificar a conta afetada.'] },
    ],
  },
};

export function generateStaticParams() { return [...Object.keys(pages), 'join'].map((slug) => ({ slug })); }

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  if (slug === 'join') return { title: 'Como entrar — MerelyMeSMP', description: 'Entra no MerelyMeSMP através do Minecraft Java Edition.', alternates: { canonical: '/pt/join', languages: { en: '/join', 'pt-PT': '/pt/join' } } };
  const page = pages[slug];
  return page ? { title: `${page.title} — MerelyMeSMP`, description: page.intro, alternates: { canonical: `/pt/${slug}`, languages: { en: `/${slug}`, 'pt-PT': `/pt/${slug}` } } } : {};
}

function JoinPage() {
  return (
    <>
      <InfoHeader locale="pt" switchHref="/join" />
      <main className="info-shell join-guide" lang="pt-PT">
        <section className="info-hero">
          <p className="eyebrow">Minecraft Java Edition · +1.21</p><h1>Entra na luta.</h1>
          <p>Usa o domínio abaixo — a porta especial da GPORTAL é configurada automaticamente através do DNS.</p>
          <div className="join-guide-actions"><CopyServerButton locale="pt" /><Link className="button button-ghost" href="/pt/community">Ver comunidade</Link></div>
          <LiveServerStatus locale="pt" />
        </section>
        <section className="steps-grid" aria-label="Como entrar">
          {[
            ['01', 'Abre o Minecraft', 'Inicia o Minecraft: Java Edition 1.21 ou superior.'],
            ['02', 'Adiciona o servidor', 'Escolhe Multiplayer e depois Add Server. Usa MerelyMeSMP como nome.'],
            ['03', 'Cola o endereço', 'Introduz merelymesmp.com exatamente. Não precisas de escrever a porta numérica.'],
            ['04', 'Entra', 'Guarda o servidor, liga-te e lê as regras antes da primeira luta.'],
          ].map(([number, title, body]) => <article key={number}><span>{number}</span><h2>{title}</h2><p>{body}</p></article>)}
        </section>
        <section className="help-card"><Server size={24} aria-hidden="true" /><div><h2>Problema de ligação?</h2><p>Confirma que estás no Java Edition. Se o servidor estiver online, envia a mensagem de erro exata ao suporte.</p></div><Link href="/pt/support">Abrir suporte <Mail size={15} aria-hidden="true" /></Link></section>
      </main>
      <SiteFooter locale="pt" />
    </>
  );
}

export default async function PortugueseInfoPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  if (slug === 'join') return <JoinPage />;
  const page = pages[slug];
  if (!page) notFound();
  return (
    <>
      <InfoHeader locale="pt" switchHref={`/${slug}`} />
      <main className="info-shell legal-page" lang="pt-PT">
        <section className="info-hero"><p className="eyebrow">{page.eyebrow}</p><h1>{page.title}</h1><p>{page.intro}</p>{page.updated && <span>Última atualização: {page.updated}</span>}</section>
        <div className="legal-layout">
          <aside><ShieldCheck size={22} aria-hidden="true" /><strong>MerelyMeSMP</strong><span>Servidor independente operado a partir de Portugal</span><a href="mailto:support@merelymesmp.com">support@merelymesmp.com</a></aside>
          <article className="legal-content">
            {page.sections.map((section) => <section key={section.title}><h2>{section.title}</h2>{section.paragraphs?.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}{section.bullets && <ul>{section.bullets.map((bullet) => <li key={bullet}>{bullet}</li>)}</ul>}</section>)}
            {slug === 'contact' && <Link className="button button-primary contact-button" href="/pt/support">Abrir centro de suporte <ExternalLink size={16} aria-hidden="true" /></Link>}
            {slug === 'bans' && <Link className="button button-primary contact-button" href="/pt/support">Abrir recurso <ExternalLink size={16} aria-hidden="true" /></Link>}
          </article>
        </div>
      </main>
      <SiteFooter locale="pt" />
    </>
  );
}
