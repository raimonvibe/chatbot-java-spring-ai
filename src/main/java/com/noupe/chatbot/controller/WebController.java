package com.noupe.chatbot.controller;

import com.noupe.chatbot.model.Chatbot;
import com.noupe.chatbot.repository.ChatbotRepository;
import com.noupe.chatbot.service.AiChatbotService;
import com.noupe.chatbot.service.WebsiteAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Optional;

/**
 * Web Controller for serving HTML pages
 */
@Controller
@RequestMapping("/")
public class WebController {
    
    private final ChatbotRepository chatbotRepository;
    private final AiChatbotService aiChatbotService;
    private final WebsiteAnalysisService websiteAnalysisService;
    
    @Autowired
    public WebController(ChatbotRepository chatbotRepository, 
                        AiChatbotService aiChatbotService,
                        WebsiteAnalysisService websiteAnalysisService) {
        this.chatbotRepository = chatbotRepository;
        this.aiChatbotService = aiChatbotService;
        this.websiteAnalysisService = websiteAnalysisService;
    }
    
    /**
     * Home page - Dashboard
     */
    @GetMapping
    public String home(Model model) {
        List<Chatbot> chatbots = chatbotRepository.findAll();
        model.addAttribute("chatbots", chatbots);
        model.addAttribute("totalChatbots", chatbots.size());
        model.addAttribute("activeChatbots", chatbots.stream().mapToInt(c -> c.getIsActive() ? 1 : 0).sum());
        return "index";
    }
    
    /**
     * Chatbot management page
     */
    @GetMapping("/chatbots")
    public String chatbots(Model model) {
        List<Chatbot> chatbots = chatbotRepository.findAll();
        model.addAttribute("chatbots", chatbots);
        return "chatbots";
    }
    
    /**
     * Create new chatbot page
     */
    @GetMapping("/chatbots/new")
    public String createChatbot(Model model) {
        model.addAttribute("chatbot", new Chatbot());
        return "chatbot-form";
    }
    
    /**
     * Edit chatbot page
     */
    @GetMapping("/chatbots/{id}/edit")
    public String editChatbot(@PathVariable Long id, Model model) {
        Optional<Chatbot> chatbot = chatbotRepository.findById(id);
        if (chatbot.isEmpty()) {
            return "redirect:/chatbots";
        }
        model.addAttribute("chatbot", chatbot.get());
        return "chatbot-form";
    }
    
    /**
     * Chatbot details page
     */
    @GetMapping("/chatbots/{id}")
    public String chatbotDetails(@PathVariable Long id, Model model) {
        Optional<Chatbot> chatbot = chatbotRepository.findById(id);
        if (chatbot.isEmpty()) {
            return "redirect:/chatbots";
        }
        
        // Get analytics
        try {
            model.addAttribute("analytics", aiChatbotService.getConversationAnalytics(id));
            model.addAttribute("analysisStats", websiteAnalysisService.getAnalysisStats(chatbot.get()));
        } catch (Exception e) {
            model.addAttribute("analytics", null);
            model.addAttribute("analysisStats", null);
        }
        
        model.addAttribute("chatbot", chatbot.get());
        return "chatbot-details";
    }
    
    /**
     * Chatbot testing page
     */
    @GetMapping("/chatbots/{id}/test")
    public String testChatbot(@PathVariable Long id, Model model) {
        Optional<Chatbot> chatbot = chatbotRepository.findById(id);
        if (chatbot.isEmpty()) {
            return "redirect:/chatbots";
        }
        model.addAttribute("chatbot", chatbot.get());
        return "chatbot-test";
    }
    
    /**
     * Analytics page
     */
    @GetMapping("/analytics")
    public String analytics(Model model) {
        List<Chatbot> chatbots = chatbotRepository.findAll();
        model.addAttribute("chatbots", chatbots);
        return "analytics";
    }
    
    /**
     * Settings page
     */
    @GetMapping("/settings")
    public String settings(Model model) {
        return "settings";
    }
}
