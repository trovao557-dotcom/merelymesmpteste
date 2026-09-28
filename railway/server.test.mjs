import assert from 'node:assert/strict';
import test from 'node:test';
import {
  applyObservation,
  emptyState,
  leaderboardFromState,
  normaliseContent,
  normaliseStatus,
  normaliseSubmission,
  statusPageFromState,
  supportNotificationFromSubmission,
} from './server.mjs';

test('records genuine player observations without inventing combat statistics', () => {
  const status = normaliseStatus(
    {
      online: true,
      players: { online: 1, max: 100, list: [{ name: 'MerelyMe', uuid: 'player-1' }] },
      version: '26.2',
      software: 'Paper',
      motd: { clean: ['MerelyMeSMP', 'FIRST SEASON'] },
    },
    '2026-08-30T18:00:00.000Z',
  );
  assert.ok(status);
  assert.equal('version' in status, false);
  assert.equal('software' in status, false);

  const first = applyObservation(emptyState('2026-08-30T18:00:00.000Z'), status, '2026-08-30T18:00:00.000Z');
  const second = applyObservation(first, status, '2026-08-30T18:01:00.000Z');
  assert.equal(leaderboardFromState(second)[0].name, 'MerelyMe');
  assert.equal(leaderboardFromState(second)[0].observedMinutes, 1);
  assert.equal('kills' in leaderboardFromState(second)[0], false);

  const publicStatus = statusPageFromState({
    ...second,
    status: { ...status, version: '26.2', software: 'Paper' },
  }).minecraft;
  assert.equal('version' in publicStatus, false);
  assert.equal('software' in publicStatus, false);
});

test('records downtime and closes the incident when the server recovers', () => {
  const online = normaliseStatus({ online: true, players: { online: 0, max: 100, list: [] } }, '2026-08-30T18:00:00.000Z');
  const offline = normaliseStatus({ online: false, players: { online: 0, max: 100, list: [] } }, '2026-08-30T18:01:00.000Z');
  let state = applyObservation(emptyState('2026-08-30T18:00:00.000Z'), online, '2026-08-30T18:00:00.000Z');
  state = applyObservation(state, offline, '2026-08-30T18:01:00.000Z');
  assert.equal(statusPageFromState(state, '2026-08-30T18:01:00.000Z').incidents[0].status, 'investigating');
  state = applyObservation(state, online, '2026-08-30T18:02:00.000Z');
  state.lastBackupDay = '2026-08-30';
  const statusPage = statusPageFromState(state, '2026-08-30T18:02:00.000Z');
  assert.equal(statusPage.incidents[0].status, 'resolved');
  assert.equal(statusPage.days[0].checks, 3);
  assert.deepEqual(statusPage.backup, { lastSuccessfulDay: '2026-08-30', retentionDays: 30, healthy: true });
});

test('sanitises editable content and support submissions', () => {
  const content = normaliseContent({
    announcement: { title: 'Hello', ctaHref: 'javascript:alert(1)' },
    events: [{ title: 'Mace Night', status: 'unknown' }],
    news: [{ title: 'Update', status: 'published', body: 'Real news' }],
    faqs: [{ question: 'Question?', answer: 'Answer.' }],
    discord: { inviteUrl: 'javascript:alert(1)', guildId: 'not-a-guild' },
  });
  assert.equal(content.announcement.ctaHref, '/community');
  assert.equal(content.events[0].status, 'coming-soon');
  assert.equal(content.discord.inviteUrl, '');

  const submission = normaliseSubmission({
    type: 'appeal',
    playerName: 'MerelyMe',
    email: 'player@example.com',
    message: 'This is a complete and valid appeal message.',
    consent: true,
    website: '',
  }, '2026-08-30T18:00:00.000Z', 'submission-1');
  assert.equal(submission.status, 'new');
  assert.equal(submission.id, 'submission-1');

  const notification = supportNotificationFromSubmission({
    ...submission,
    subject: 'Purchase\r\nBcc: attacker@example.com',
  });
  assert.match(notification.subject, /^\[MerelyMeSMP\] New appeal request/);
  assert.doesNotMatch(notification.subject, /attacker/i);
  assert.doesNotMatch(notification.text, new RegExp(submission.message));
  assert.doesNotMatch(notification.text, new RegExp(submission.email));
});
