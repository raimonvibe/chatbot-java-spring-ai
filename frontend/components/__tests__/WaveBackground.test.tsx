import { render } from '@testing-library/react';
import WaveBackground from '../WaveBackground';

describe('WaveBackground', () => {
  it('should render without crashing', () => {
    const { container } = render(<WaveBackground />);
    expect(container.firstChild).toBeInTheDocument();
  });

  it('should render wave SVG elements', () => {
    const { container } = render(<WaveBackground />);
    const svgElements = container.querySelectorAll('svg');
    expect(svgElements.length).toBeGreaterThan(0);
  });

  it('should render floating particles', () => {
    const { container } = render(<WaveBackground />);
    const particles = container.querySelectorAll('.absolute.w-2.h-2');
    expect(particles.length).toBe(5);
  });

  it('should have correct CSS classes for positioning', () => {
    const { container } = render(<WaveBackground />);
    const mainContainer = container.firstChild as HTMLElement;
    expect(mainContainer).toHaveClass('absolute', 'inset-0', 'overflow-hidden', 'pointer-events-none');
  });
});

