package ru.practicum.shareit.item.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.Booking;
import ru.practicum.shareit.booking.BookingRepository;
import ru.practicum.shareit.booking.dto.BookingShortDto;
import ru.practicum.shareit.exception.BadRequestException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.NotOwnerException;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.CommentRequestDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.ItemRequestDto;
import ru.practicum.shareit.item.dto.mapper.CommentMapper;
import ru.practicum.shareit.item.dto.mapper.ItemMapper;
import ru.practicum.shareit.item.model.Comment;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.CommentRepository;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final CommentRepository commentRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public ItemDto create(ItemRequestDto itemDto, long userId) {
        log.debug("Creating item: {}; for user {}", itemDto, userId);

        User user = userRepository.findById(userId).orElseThrow(() ->
                new NotFoundException("User not found"));
        Item item = ItemMapper.toItem(itemDto);
        item.setOwner(user);

        return ItemMapper.toItemDto(itemRepository.save(item));
    }

    @Override
    @Transactional
    public ItemDto update(long itemId, ItemDto itemDto, long userId) {
        log.debug("Updating item with id: {} for user {}", itemId, userId);
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new NotFoundException("Item not found"));
        if (item.getOwner().getId() == userId) {
            if (itemDto.getName() != null && !itemDto.getName().isBlank()) {
                item.setName(itemDto.getName());
            }
            if (itemDto.getDescription() != null && !itemDto.getDescription().isBlank()) {
                item.setDescription(itemDto.getDescription());
            }
            if (itemDto.getAvailable() != null) {
                item.setAvailable(itemDto.getAvailable());
            }
            Item updatedItem = itemRepository.save(item);
            return ItemMapper.toItemDto(updatedItem);
        } else {
            throw new NotOwnerException("User is not the owner of the item");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ItemDto getItemById(long itemId, long userId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        ItemDto itemDto = ItemMapper.toItemDto(item);

        if (item.getOwner().getId().equals(user.getId())) {
            setLastAndNextBookingsForItem(itemDto);
        }

        List<Comment> comments = commentRepository.findAllByItemId(itemId);
        itemDto.setComments(CommentMapper.listOfComments(comments));

        return itemDto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemDto> getItemsByUserId(Long userId) {
        log.debug("Getting items by user Id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format("User with not found")));

        List<Item> items = itemRepository.findByOwner(user, Sort.by("id").ascending());

        if (items.isEmpty()) {
            throw new NotFoundException(String.format("No items found for user"));
        }

        return setLastAndNextBookingsForItemList(items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemDto> getItemsBySearch(String text) {
        log.debug("Getting items by search: {}", text);
        if (text.isBlank()) {
            return Collections.emptyList();
        }
        List<Item> items = itemRepository.findAllByAvailableTrueAndNameContainingIgnoreCaseOrAvailableTrueAndDescriptionContainingIgnoreCase(text, text); // Поиск Item по тексту
        return items.stream()
                .map(ItemMapper::toItemDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CommentDto postComment(Long userId, Long itemId, CommentRequestDto commentRequestDto) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item id %d not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User id %d not found"));

        if (!bookingRepository.existsByItemAndBookerAndEndIsBefore(item, user, LocalDateTime.now())) {
            throw new BadRequestException("User cannot post comments on item without a valid booking");
        }
        Comment comment = Comment.builder()
                .item(item)
                .author(user)
                .text(commentRequestDto.getText())
                .created(LocalDateTime.now())
                .build();

        comment = commentRepository.save(comment);
        return CommentMapper.responseDtoOf(comment);
    }


    private void setLastAndNextBookingsForItem(ItemDto itemDto) {
        List<Booking> itemBookings = itemRepository.findById(itemDto.getId())
                .map(Item::getBookings)
                .orElse(Collections.emptyList());

        itemDto.setLastBooking(findLastBookingForItem(itemBookings));
        itemDto.setNextBooking(findNextBookingForItem(itemBookings, getItemIdsForBookings(itemBookings)));
    }

    private List<Long> getItemIdsForBookings(List<Booking> bookings) {
        return bookings.stream()
                .map(booking -> booking.getItem().getId())
                .collect(Collectors.toList());
    }

    private BookingShortDto findLastBookingForItem(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        Booking lastBooking = bookings.stream()
                .filter(booking -> !booking.getStart().isAfter(now))
                .max(Comparator.comparing(Booking::getStart))
                .orElse(null);

        if (lastBooking != null) {
            return BookingShortDto.builder()
                    .id(lastBooking.getId())
                    .bookerId(lastBooking.getBooker().getId())
                    .start(lastBooking.getStart())
                    .end(lastBooking.getEnd())
                    .itemId(lastBooking.getItem().getId())
                    .build();
        } else {
            return null;
        }
    }

    private BookingShortDto findNextBookingForItem(List<Booking> bookings, List<Long> itemIds) {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> approvedBookings = bookingRepository.findApprovedByItems(itemIds);
        Optional<Booking> nextBooking = bookings.stream()
                .filter(booking -> booking.getStart().isAfter(now) && approvedBookings.contains(booking))
                .min(Comparator.comparing(Booking::getStart));
        return nextBooking.map(booking -> BookingShortDto.builder()
                        .id(booking.getId())
                        .bookerId(booking.getBooker().getId())
                        .start(booking.getStart())
                        .end(booking.getEnd())
                        .itemId(booking.getItem().getId())
                        .build())
                .orElse(null);
    }

    private List<ItemDto> setLastAndNextBookingsForItemList(List<Item> items) {
        List<Long> itemIds = items.stream()
                .map(Item::getId)
                .collect(Collectors.toList());
        Map<Long, List<Booking>> mapBookings = bookingRepository.findAllByItem_IdIn(itemIds, Sort.by(Sort.Direction.ASC, "start"))
                .stream()
                .collect(Collectors.groupingBy(booking -> booking.getItem().getId()));

        return items.stream()
                .map(item -> {
                    ItemDto itemDto = ItemMapper.toItemDto(item);
                    List<Booking> itemBookings = mapBookings.getOrDefault(item.getId(), new ArrayList<>());
                    setLastAndNextBookingsForItem(itemDto, itemBookings, itemIds);
                    return itemDto;
                })
                .collect(Collectors.toList());
    }

    private void setLastAndNextBookingsForItem(ItemDto itemDto, List<Booking> itemBookings, List<Long> itemIds) {
        itemDto.setLastBooking(findLastBookingForItem(itemBookings));
        itemDto.setNextBooking(findNextBookingForItem(itemBookings, itemIds));
    }
}
