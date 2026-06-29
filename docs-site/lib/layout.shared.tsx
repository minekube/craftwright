import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';

export function baseOptions(): BaseLayoutProps {
  return {
    nav: {
      title: 'Craftless',
    },
    links: [
      {
        text: 'GitHub',
        url: 'https://github.com/minekube/craftless',
      },
    ],
  };
}
