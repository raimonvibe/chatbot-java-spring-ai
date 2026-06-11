package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.WebsiteAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Optional;

/**
 * Web Controller for serving HTML pages
 * Note: Root path (/) is handled by RootController (returns JSON API info)
 *
 * <p>SECURITY: every chatbot listing/detail route is scoped to the authenticated owner
 * (admins see everything) — mirrors the ownership checks of the REST API.
 */
@Controller
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

    /** Resolve the authenticated domain user from either the OAuth2 or JWT principal. */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomOAuth2User oauthUser) {
            return oauthUser.getUser();
        }
        if (principal instanceof User user) {
            return user;
        }
        return null;
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** Chatbots visible to the current user: own bots, or all bots for admins. */
    private List<Chatbot> visibleChatbots() {
        if (isAdmin()) {
            return chatbotRepository.findAll();
        }
        User user = currentUser();
        if (user == null || user.getId() == null) {
            return List.of();
        }
        return chatbotRepository.findByOwnerId(user.getId());
    }

    /** A single chatbot only if owned by the current user (or admin); empty otherwise. */
    private Optional<Chatbot> ownedChatbot(Long id) {
        if (isAdmin()) {
            return chatbotRepository.findById(id);
        }
        User user = currentUser();
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        return chatbotRepository.findByIdAndOwner_Id(id, user.getId());
    }

    /**
     * Home page - Dashboard
     * Note: Root path (/) is handled by RootController
     */
    @GetMapping("/index")
    public String home(Model model) {
        List<Chatbot> chatbots = visibleChatbots();
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
        model.addAttribute("chatbots", visibleChatbots());
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
        Optional<Chatbot> chatbot = ownedChatbot(id);
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
        Optional<Chatbot> chatbot = ownedChatbot(id);
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
        Optional<Chatbot> chatbot = ownedChatbot(id);
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
        model.addAttribute("chatbots", visibleChatbots());
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
