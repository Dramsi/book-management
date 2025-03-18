package io.github.alexshamrai.e2e;

import io.github.alexshamrai.client.BookClient;
import io.github.alexshamrai.client.OrderClient;
import io.github.alexshamrai.dto.BookDto;
import io.github.alexshamrai.dto.OrderDto;
import io.github.alexshamrai.dto.OrderItemRequest;
import io.github.alexshamrai.dto.OrderRequest;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Console;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static io.github.alexshamrai.e2e.BaseTest.BOOK_SERVICE_URL;
import static io.github.alexshamrai.e2e.BaseTest.ORDER_SERVICE_URL;
import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {
    private final BookClient bookClient = new BookClient(BOOK_SERVICE_URL);
    private final OrderClient orderClient = new OrderClient(ORDER_SERVICE_URL);
    private BookDto testBook;

    private final Double price = 29.99;

    private BookDto createBook(String title, String author, double price, int stock) {
        BookDto bookRequest = BookDto.builder()
                .title(title)
                .author(author)
                .price(price)
                .stockQuantity(stock)
                .build();

        Response createBookResponse = bookClient.createBook(bookRequest);
        createBookResponse.then().statusCode(200);
        return createBookResponse.as(BookDto.class);
    }

    private Response createOrderResponse(long bookId, int quantity) {
        OrderItemRequest orderItemRequest = OrderItemRequest.builder()
                .bookId(bookId)
                .quantity(quantity)
                .build();

        OrderRequest orderRequest = OrderRequest.builder()
                .items(Collections.singletonList(orderItemRequest))
                .build();

        return orderClient.createOrder(orderRequest);
    }

    @BeforeEach
    void setUp() {
        orderClient.deleteAllOrders()
                .then()
                .statusCode(204);

        bookClient.deleteAllBooks()
                .then()
                .statusCode(204);

        testBook = createBook("Test Book", "Test Author", price, 10);
    }

    @AfterEach
    void tearDown() {
        orderClient.deleteAllOrders()
                .then()
                .statusCode(204);

        bookClient.deleteAllBooks()
                .then()
                .statusCode(204);
    }

    @Test
    void shouldBookExists() {
        assertThat(testBook.getId()).isNotNull();
    }

    @Test
    void should404WhenOrderNotFound() {
        Response response = orderClient.getOrder(999L);
        response.then().statusCode(404);
    }

    @Test
    void shouldCreateOrderForBook() {
        Response createOrderResponse = createOrderResponse(testBook.getId(), 2);
        createOrderResponse.then().statusCode(200);

        OrderDto createdOrder = createOrderResponse.as(OrderDto.class);
        assertThat(createdOrder.getId()).isNotNull();

        Response getOrderResponse = orderClient.getOrder(createdOrder.getId());
        getOrderResponse.then().statusCode(200);

        OrderDto fetchedOrder = getOrderResponse.as(OrderDto.class);
        assertThat(fetchedOrder.getId()).isEqualTo(createdOrder.getId());
    }

    @Test
    void shouldOrderHaveOrderedItems() {
        Response createOrderResponse = createOrderResponse(testBook.getId(), 2);
        createOrderResponse.then().statusCode(200);

        OrderDto createdOrder = createOrderResponse.as(OrderDto.class);

        Response getOrderResponse = orderClient.getOrder(createdOrder.getId());
        getOrderResponse.then().statusCode(200);

        OrderDto fetchedOrder = getOrderResponse.as(OrderDto.class);
        assertThat(fetchedOrder.getOrderItems()).hasSize(1);
        assertThat(fetchedOrder.getOrderItems().get(0).getBookId()).isEqualTo(testBook.getId());
        assertThat(fetchedOrder.getOrderItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(fetchedOrder.getOrderItems().get(0).getPrice()).isEqualTo(price);
    }

    @Test
    void shouldHaveOrderStatusPending() {
        Response createOrderResponse = createOrderResponse(testBook.getId(), 2);
        createOrderResponse.then().statusCode(200);

        OrderDto createdOrder = createOrderResponse.as(OrderDto.class);

        Response getOrderResponse = orderClient.getOrder(createdOrder.getId());
        getOrderResponse.then().statusCode(200);

        OrderDto fetchedOrder = getOrderResponse.as(OrderDto.class);
        assertThat(fetchedOrder.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldCorrectlyTotalAmount() {
        Response createOrderResponse = createOrderResponse(testBook.getId(), 2);
        createOrderResponse.then().statusCode(200);

        OrderDto createdOrder = createOrderResponse.as(OrderDto.class);

        Response getOrderResponse = orderClient.getOrder(createdOrder.getId());
        getOrderResponse.then().statusCode(200);

        OrderDto fetchedOrder = getOrderResponse.as(OrderDto.class);
        assertThat(fetchedOrder.getTotalAmount()).isEqualTo(price * 2);
    }

    @Test
    void shouldCorrectlyOrderDate() {
        Response createOrderResponse = createOrderResponse(testBook.getId(), 2);
        createOrderResponse.then().statusCode(200);

        OrderDto createdOrder = createOrderResponse.as(OrderDto.class);

        LocalDateTime expectedOrderDate = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        Response getOrderResponse = orderClient.getOrder(createdOrder.getId());
        getOrderResponse.then().statusCode(200);

        OrderDto fetchedOrder = getOrderResponse.as(OrderDto.class);

        LocalDateTime actualOrderDate = fetchedOrder.getOrderDate().truncatedTo(ChronoUnit.MINUTES);

        assertThat(actualOrderDate)
                .isBetween(expectedOrderDate.minusMinutes(1), expectedOrderDate.plusMinutes(1));
    }

    @Test
    void shouldDecreaseStockQuantity() {
        Response createOrderResponse = createOrderResponse(testBook.getId(), 2);
        createOrderResponse.then().statusCode(200);

        Response getBookResponse = bookClient.getBook(testBook.getId());
        getBookResponse.then().statusCode(200);
        BookDto updatedBook = getBookResponse.as(BookDto.class);

        assertThat(updatedBook.getStockQuantity()).isEqualTo(8);
    }

    @Test
    void shouldNotOrderIfStockIsLow() {
        BookDto limitedBook = createBook("Limited Book", "Limited Author", price, 1);

        Response createOrderResponse = createOrderResponse(limitedBook.getId(), 2);

        assertThat(createOrderResponse.getStatusCode()).isNotEqualTo(200);
    }

    @Test
    void shouldNotOrderWhenOutStock() {
        BookDto emptyBook = createBook("Empty Book", "Empty Author", price, 0);

        Response createOrderResponse = createOrderResponse(emptyBook.getId(), 1);

        assertThat(createOrderResponse.getStatusCode()).isNotEqualTo(200);
    }
}