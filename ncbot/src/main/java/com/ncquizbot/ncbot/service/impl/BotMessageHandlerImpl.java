package com.ncquizbot.ncbot.service.impl;

import com.ncquizbot.ncbot.bot.Bot;
import com.ncquizbot.ncbot.model.Answer;
import com.ncquizbot.ncbot.model.Option;
import com.ncquizbot.ncbot.model.Question;
import com.ncquizbot.ncbot.model.User;
import com.ncquizbot.ncbot.qrcode.QrCodeGenerator;
import com.ncquizbot.ncbot.qrcode.QrCodeGeneratorImpl;
import com.ncquizbot.ncbot.service.BotMessageHandler;
import com.ncquizbot.ncbot.service.QuestionService;
import com.ncquizbot.ncbot.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.methods.ParseMode;
import org.telegram.telegrambots.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.send.SendPhoto;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class BotMessageHandlerImpl implements BotMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);
    private static final String HELLO_MESSAGE = "Привет! \n" +
            "Давай знакомиться &#128512 \n" +
            "Я – телеграм бот компании Netcracker.\n" +
            "И сегодня у тебя есть шанс проверить свои знания и логику перед зачислением на кафедру ИСС и компанию Netcracker.\n" +
            "После прохождения всех заданий ты увидишь количество набранных баллов.\n" +
            "Удачи! &#128521;";
    public static final String GOODBYE_MESSAGE = "\nСпасибо за прохождение нашего квиза. Надеюсь, это было познавательно &#128512;\n" +
            "Начиная с 20 апреля, мы начнем приглашать студентов на собеседования.\n" +
            "Обращаем внимание, что отбор на кафедру проходит на конкурсной основе.\n" +
            "По всем вопросам можно обращаться к нам по почте: EduCenter@NetCracker.com";
    public static final String USER_SCORE = "Спасибо! Это был последний вопрос. Твой результат: ";
    //public static final String COMMAND_PRESENT = "present";
    public static final String COMMAND_GO = "go";
    public static final String COMMAND_NAME = "name";
    public static final String FINISH_GAME = "You have finished your game! We will happy to meet you at another time";
    public static final String NAME_MESSAGE = "Давай знакомиться, напиши свои имя, фамилию и отчество";
    public static final String GO_MESSAGE = "Вот всё и готово. Если ты готов начать, нажми кнопку go";
    @Autowired
    private UserService userService;
    @Autowired
    private QuestionService questionService;

    @Override
    public PartialBotApiMethod handleMessage(Update update) {
        Message message = update.getMessage();
        return handleInputMessage(message);

    }

    private PartialBotApiMethod handleInputMessage(Message message) {
        if (Objects.nonNull(message) && message.hasText()) {
            ReplyKeyboardMarkup replyKeyboardMarkup = null;
            String currentMessageText = message.getText();
            String ouputMessageText = "";
            User user = userService.createAndSaveUserByTelegramMessageIfCurrentDoesNotExist(message);
            if (message.getText().equals(COMMAND_GO)) {
                userService.setActiveStatusTrue(user);
            }
//            if (message.getText().equals(COMMAND_PRESENT)) {
//                if (user.isGameOver()) {
//                    if (!user.isPresentGiven()) {
//                        return getQrCodeImageForPresent(user);
//                    }
//                }
//            }
            if (user.isActiveNow()) {
                userService.updateLastUserSessionDate(user);
                if (user.getQuestionNumber() > 0) {
                    updateUserScore(user, currentMessageText);
                }
                Question nextQuestion = getQuestionForUser(user);
                if (user.getQuestionNumber() > 5 || Objects.isNull(nextQuestion)) {
                    ouputMessageText = getGoodByeMessage(user);
//                    replyKeyboardMarkup = new ReplyKeyboardMarkup();
//                    replyKeyboardMarkup.setOneTimeKeyboard(true);
//                    List<KeyboardRow> keyboardRowList = new ArrayList<>();
//                    KeyboardRow keyboardRow = new KeyboardRow();
//                    KeyboardButton keyboardButton = new KeyboardButton();
//                    keyboardButton.setText(COMMAND_PRESENT);
//                    keyboardRow.add(keyboardButton);
//                    keyboardRowList.add(keyboardRow);
//                    replyKeyboardMarkup.setKeyboard(keyboardRowList);
                    return getSendMessageForBot(ouputMessageText, message, replyKeyboardMarkup).setParseMode(ParseMode.HTML);
                } else {
                    if (nextQuestion.getOptions().size() > 1) {
                        replyKeyboardMarkup = getQuestionWithMultipleOptions(nextQuestion.getOptions());
                    }
                    ouputMessageText = nextQuestion.getContent();
                }
            } else if (!user.isGameOver()) {
                if (message.getText().equals(COMMAND_NAME)) {
                    ouputMessageText = NAME_MESSAGE;
                }
                else if (message.getText().equals("/start")){
                    ouputMessageText = HELLO_MESSAGE;
                    replyKeyboardMarkup = new ReplyKeyboardMarkup();
                    replyKeyboardMarkup.setOneTimeKeyboard(true);
                    List<KeyboardRow> keyboardRowList = new ArrayList<>();
                    KeyboardRow keyboardRow = new KeyboardRow();
                    KeyboardButton keyboardButton = new KeyboardButton();
                    keyboardButton.setText(COMMAND_NAME);
                    keyboardRow.add(keyboardButton);
                    keyboardRowList.add(keyboardRow);
                    replyKeyboardMarkup.setKeyboard(keyboardRowList);
                }
                else {
                    String realName = message.getText();
                    userService.setRealNameForUser(user, realName);
                }
                return getSendMessageForBot(ouputMessageText, message, replyKeyboardMarkup).setParseMode(ParseMode.HTML);
            } else {
                ouputMessageText = FINISH_GAME;
            }
            return getSendMessageForBot(ouputMessageText, message, replyKeyboardMarkup);
        }
        return null;
    }

    private void updateUserScore(User user, String userAnswerText) {
        Question lastQuestion = questionService.findQuestionById(user.getCurrentQuestionId());
        Integer questionWeight = lastQuestion.getWeight();
        Answer answer = lastQuestion.getAnswer();
        String answerText = Objects.isNull(answer) ? "" : answer.getContent();
        if (checkAnswer(userAnswerText, answerText)) {
            userService.increaseUserScore(user, questionWeight);
        }
    }

    private SendMessage getSendMessageForBot(String ouputMessageText, Message message, ReplyKeyboardMarkup replyKeyboardMarkup) {
        SendMessage sendMessage = null;
        if (Objects.nonNull(ouputMessageText)) {
            sendMessage = new SendMessage();
            sendMessage.setText(ouputMessageText)
                    .setChatId(message.getChatId());
            sendMessage.setReplyMarkup(replyKeyboardMarkup);
            sendMessage.enableWebPagePreview();
        }
        return sendMessage;
    }

    private SendPhoto getQrCodeImageForPresent(User user) {
        QrCodeGenerator qrCodeGenerator = new QrCodeGeneratorImpl();
        qrCodeGenerator.processGeneratingQrCode(user);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(qrCodeGenerator.getQrCode());
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(user.getChatId())
                .setNewPhoto("qr code for present", byteArrayInputStream);
        return sendPhoto;
    }

    private String getGoodByeMessage(User user) {
        userService.turnOffUserActivityStatus(user);
        userService.updateUserSessionEndDate(user);
        userService.setGameOverForUser(user);
        return getGoodbyeMessage(user.getScore());
    }

    private Question getQuestionForUser(User user) {
        return userService.setNextQuestionToUser(user);
    }

    private ReplyKeyboardMarkup getQuestionWithMultipleOptions(List<Option> options) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        List<KeyboardRow> keyboardRowList = new ArrayList<>();
        KeyboardRow keyboardRow = new KeyboardRow();
        for (Option option : options) {
            keyboardRow.add(option.getContent());
        }
        keyboardRowList.add(keyboardRow);
        replyKeyboardMarkup.setKeyboard(keyboardRowList);
        return replyKeyboardMarkup;
    }

    private boolean checkAnswer(String userAnswer, String answer) {
        userAnswer = userAnswer.toLowerCase();
        answer = answer.toLowerCase();
        if (userAnswer.equals(answer)) {
            return true;
        }
        return false;
    }

    private String getGoodbyeMessage(Integer userScore) {
        return USER_SCORE + userScore + GOODBYE_MESSAGE;
    }
}
