import type { Metadata } from 'next';
import { PlayerProfile } from '@/components/player-profile';
import { communityApiUrl } from '@/lib/community';

export const dynamic = 'force-dynamic';

export async function generateMetadata({ params }: { params: Promise<{ uuid: string }> }): Promise<Metadata> {
  const { uuid } = await params;
  try {
    const response = await fetch(`${communityApiUrl}/api/players/${encodeURIComponent(uuid)}`, { signal: AbortSignal.timeout(6_000) });
    if (response.ok) {
      const profile = await response.json() as { player: { name: string } };
      const title = `${profile.player.name} — MerelyMeSMP`;
      const description = `Perfil de atividade observada de ${profile.player.name} no MerelyMeSMP.`;
      return { title, description, alternates: { canonical: `/pt/players/${uuid}`, languages: { en: `/players/${uuid}`, 'pt-PT': `/pt/players/${uuid}` } }, openGraph: { title, description, images: [] }, twitter: { card: 'summary', title, description, images: [] } };
    }
  } catch {}
  return { title: 'Perfil de jogador — MerelyMeSMP', robots: { index: false, follow: true } };
}

export default function PortuguesePlayerProfilePage() { return <PlayerProfile locale="pt" />; }
