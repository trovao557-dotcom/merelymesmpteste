'use client';

import type { ComponentProps } from 'react';

export default function SiteLink({ children, ...props }: ComponentProps<'a'>) {
  return <a {...props}>{children}</a>;
}
