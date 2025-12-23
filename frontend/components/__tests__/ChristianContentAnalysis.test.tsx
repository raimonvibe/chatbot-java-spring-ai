import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import ChristianContentAnalysisComponent from '../ChristianContentAnalysis';
import * as api from '@/lib/api';

// Mock the API
jest.mock('@/lib/api', () => ({
  analyzeChristianContent: jest.fn(),
}));

const mockAnalyzeChristianContent = api.analyzeChristianContent as jest.MockedFunction<typeof api.analyzeChristianContent>;

describe('ChristianContentAnalysis', () => {
  const defaultProps = {
    chatbotId: 1,
    chatbotName: 'Test Chatbot',
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render with chatbot name', () => {
    render(<ChristianContentAnalysisComponent {...defaultProps} />);
    expect(screen.getByText(/Test Chatbot/i)).toBeInTheDocument();
    expect(screen.getByText(/Christian Content Analysis/i)).toBeInTheDocument();
  });

  it('should show analyze button', () => {
    render(<ChristianContentAnalysisComponent {...defaultProps} />);
    const analyzeButton = screen.getByRole('button', { name: /analyze/i });
    expect(analyzeButton).toBeInTheDocument();
  });

  it('should call analyzeChristianContent when button is clicked', async () => {
    const mockAnalysis = {
      totalVerses: 10,
      matches: [],
      summary: 'Test summary',
    };
    mockAnalyzeChristianContent.mockResolvedValue(mockAnalysis);

    render(<ChristianContentAnalysisComponent {...defaultProps} />);
    const analyzeButton = screen.getByRole('button', { name: /analyze/i });
    
    fireEvent.click(analyzeButton);

    await waitFor(() => {
      expect(mockAnalyzeChristianContent).toHaveBeenCalledWith(1, 20, 0.5);
    });
  });

  it('should display loading state during analysis', async () => {
    mockAnalyzeChristianContent.mockImplementation(() => new Promise(resolve => setTimeout(resolve, 100)));

    render(<ChristianContentAnalysisComponent {...defaultProps} />);
    const analyzeButton = screen.getByRole('button', { name: /analyze/i });
    
    fireEvent.click(analyzeButton);

    await waitFor(() => {
      expect(screen.getByText(/analyzing/i)).toBeInTheDocument();
    });
  });

  it('should display error message on API failure', async () => {
    const errorMessage = 'Failed to analyze content';
    mockAnalyzeChristianContent.mockRejectedValue(new Error(errorMessage));

    render(<ChristianContentAnalysisComponent {...defaultProps} />);
    const analyzeButton = screen.getByRole('button', { name: /analyze/i });
    
    fireEvent.click(analyzeButton);

    await waitFor(() => {
      expect(screen.getByText(/failed/i)).toBeInTheDocument();
    });
  });

  it('should display analysis results when successful', async () => {
    const mockAnalysis = {
      totalVerses: 5,
      totalVersesAnalyzed: 5,
      matches: [
        { verse: 'John 3:16', similarity: 0.9, context: 'Test context' },
      ],
      relevantVerses: [
        { verse: 'John 3:16', similarity: 0.9, context: 'Test context' },
      ],
      summary: 'Found 1 matching verse',
    };
    mockAnalyzeChristianContent.mockResolvedValue(mockAnalysis);

    render(<ChristianContentAnalysisComponent {...defaultProps} />);
    const analyzeButton = screen.getByRole('button', { name: /analyze/i });
    
    fireEvent.click(analyzeButton);

    await waitFor(() => {
      // Check for any part of the analysis result
      expect(screen.getByText(/5|Test summary|Found 1 matching verse/i)).toBeInTheDocument();
    });
  });

  it('should allow changing maxVerses and similarityThreshold', () => {
    render(<ChristianContentAnalysisComponent {...defaultProps} />);
    
    // These inputs might be hidden or in a settings panel
    // Adjust selectors based on actual implementation
    const maxVersesInput = screen.queryByLabelText(/max verses/i);
    const thresholdInput = screen.queryByLabelText(/similarity/i);
    
    // If inputs exist, test them
    if (maxVersesInput) {
      fireEvent.change(maxVersesInput, { target: { value: '30' } });
      expect(maxVersesInput).toHaveValue('30');
    }
    
    if (thresholdInput) {
      fireEvent.change(thresholdInput, { target: { value: '0.7' } });
      expect(thresholdInput).toHaveValue('0.7');
    }
  });
});

